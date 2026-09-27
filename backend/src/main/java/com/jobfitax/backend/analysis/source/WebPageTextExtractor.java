package com.jobfitax.backend.analysis.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebPageTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(WebPageTextExtractor.class);

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_RESPONSE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_ANALYSIS_CHARACTERS = 50_000;
    private static final int MAX_PREVIEW_CHARACTERS = 100_000;
    private static final String USER_AGENT = "JobFitAX/1.0 (+document analysis)";

    private static final Map<String, String> REMOVAL_SELECTORS = Map.of(
            "스크립트·스타일", "script, style, noscript, template, canvas, svg",
            "상단·하단 영역", "header, footer",
            "내비게이션", "nav, [role=navigation]",
            "보조 영역", "aside, form, dialog, iframe",
            "광고·팝업·추천 영역", ".advertisement, .advert, .ad, .banner, .cookie, .popup, .modal, "
                    + ".sidebar, .related, .recommend, .recommended, .social, "
                    + "#advertisement, #advert, #ad, #banner, #cookie, #popup, #modal, "
                    + "#sidebar, #related, #recommend, #recommended, #social"
    );

    private final HttpClient httpClient;
    private final PublicUrlValidator urlValidator;
    private final TextNormalizer textNormalizer;
    private final RenderedWebPageFetcher renderedWebPageFetcher;
    private final AuthenticationPageDetector authenticationPageDetector;

    public WebPageTextExtractor(
            PublicUrlValidator urlValidator,
            TextNormalizer textNormalizer,
            RenderedWebPageFetcher renderedWebPageFetcher,
            AuthenticationPageDetector authenticationPageDetector
    ) {
        this.urlValidator = urlValidator;
        this.textNormalizer = textNormalizer;
        this.renderedWebPageFetcher = renderedWebPageFetcher;
        this.authenticationPageDetector = authenticationPageDetector;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public UrlExtractionResult extract(String sourceUrl) {
        return extract(sourceUrl, List.of());
    }

    public UrlExtractionResult extract(String sourceUrl, List<String> preferredContentSelectors) {
        return extractDetailed(sourceUrl, preferredContentSelectors).extraction();
    }

    public WebPageExtraction extractDetailed(String sourceUrl, List<String> preferredContentSelectors) {
        try {
            RenderedWebPageFetcher.RenderedPage renderedPage = renderedWebPageFetcher.fetch(sourceUrl);
            UrlExtractionResult renderedResult = extractContent(
                    sourceUrl,
                    new FetchedPage(renderedPage.uri(), renderedPage.html()),
                    preferredContentSelectors
            );
            if (!"NO_TEXT".equals(renderedResult.status())) {
                return new WebPageExtraction(renderedResult, renderedPage.frames());
            }
        } catch (IllegalArgumentException exception) {
            return new WebPageExtraction(
                    failed(sourceUrl, "BLOCKED", exception.getMessage()),
                    List.of()
            );
        } catch (Exception exception) {
            log.info("Browser rendering failed; retrying with static HTML");
        }

        try {
            FetchedPage fetchedPage = fetch(sourceUrl);
            return new WebPageExtraction(
                    extractContent(sourceUrl, fetchedPage, preferredContentSelectors),
                    List.of()
            );
        } catch (IllegalArgumentException exception) {
            return new WebPageExtraction(
                    failed(sourceUrl, "BLOCKED", exception.getMessage()),
                    List.of()
            );
        } catch (Exception exception) {
            return new WebPageExtraction(
                    failed(sourceUrl, "FAILED", "공개 페이지 내용을 가져오지 못했습니다."),
                    List.of()
            );
        }
    }

    private FetchedPage fetch(String sourceUrl) throws IOException, InterruptedException {
        URI current = urlValidator.validate(sourceUrl);

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Encoding", "identity")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("리다이렉트 주소가 없습니다."));
                response.body().close();
                current = urlValidator.validate(current.resolve(location).toString());
                continue;
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("페이지 응답 상태: " + response.statusCode());
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!(contentType.contains("text/html") || contentType.contains("application/xhtml+xml"))) {
                response.body().close();
                throw new IllegalArgumentException("HTML 공개 페이지만 URL 자료로 사용할 수 있습니다.");
            }

            String contentEncoding = response.headers().firstValue("content-encoding").orElse("");
            try (InputStream responseBody = response.body();
                    InputStream body = "gzip".equalsIgnoreCase(contentEncoding)
                            ? new GZIPInputStream(responseBody)
                            : responseBody) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("페이지 크기가 제한을 초과했습니다.");
                }
                return new FetchedPage(current, new String(bytes, StandardCharsets.UTF_8));
            }
        }

        throw new IOException("리다이렉트 횟수가 너무 많습니다.");
    }

    private UrlExtractionResult extractContent(
            String sourceUrl,
            FetchedPage fetchedPage,
            List<String> preferredContentSelectors
    ) {
        Document document = Jsoup.parse(fetchedPage.html(), fetchedPage.uri().toString());
        log.info(
                "Public URL received: htmlCharacters={}, visibleCharacters={}",
                fetchedPage.html().length(),
                document.body() == null ? 0 : document.body().text().length()
        );
        String pageTitle = document.title();
        if (pageTitle.isBlank() && document.selectFirst("h1") != null) {
            pageTitle = document.selectFirst("h1").text();
        }
        String rawText = renderText(document.body());
        Document cleanedDocument = document.clone();
        Map<String, Integer> removedByCategory = removeNoise(cleanedDocument);
        Element mainContent = selectMainContent(cleanedDocument, preferredContentSelectors);
        String selectedText = renderText(mainContent);
        NormalizationResult normalized = textNormalizer.normalize(selectedText);

        if (authenticationPageDetector.isAuthenticationPage(normalized.text())) {
            return new UrlExtractionResult(
                    sourceUrl,
                    fetchedPage.uri().toString(),
                    pageTitle,
                    "AUTH_REQUIRED",
                    rawText.length(),
                    0,
                    0,
                    rawText.length(),
                    0,
                    false,
                    "",
                    "",
                    List.of(),
                    "로그인 화면이 확인되었습니다. 로그인 없이 열 수 있도록 페이지 공개 설정을 확인해 주세요."
            );
        }

        if (normalized.text().length() < 80) {
            return failed(sourceUrl, "NO_TEXT", "본문을 충분히 찾지 못했습니다. JavaScript로 생성되는 페이지일 수 있습니다.");
        }

        int cleanedCount = normalized.text().length();
        boolean truncated = cleanedCount > MAX_ANALYSIS_CHARACTERS;
        String analysisText = truncated
                ? normalized.text().substring(0, MAX_ANALYSIS_CHARACTERS)
                : normalized.text();
        String rawPreview = rawText.length() > MAX_PREVIEW_CHARACTERS
                ? rawText.substring(0, MAX_PREVIEW_CHARACTERS)
                : rawText;

        List<RemovalStat> stats = removedByCategory.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new RemovalStat(entry.getKey(), entry.getValue()))
                .toList();

        return new UrlExtractionResult(
                sourceUrl,
                fetchedPage.uri().toString(),
                pageTitle,
                "SUCCESS",
                rawText.length(),
                cleanedCount,
                analysisText.length(),
                Math.max(0, rawText.length() - cleanedCount),
                Math.max(0, cleanedCount - analysisText.length()),
                truncated,
                rawPreview,
                analysisText,
                stats,
                null
        );
    }

    private Map<String, Integer> removeNoise(Document document) {
        Map<String, Integer> removed = new LinkedHashMap<>();
        REMOVAL_SELECTORS.forEach((category, selector) -> {
            Elements elements = document.select(selector);
            int count = elements.stream().mapToInt(element -> element.text().length()).sum();
            elements.remove();
            removed.put(category, count);
        });
        return removed;
    }

    private Element selectMainContent(Document document, List<String> preferredContentSelectors) {
        if (preferredContentSelectors != null && !preferredContentSelectors.isEmpty()) {
            Element preferred = document.select(String.join(", ", preferredContentSelectors)).stream()
                    .filter(element -> element.text().length() >= 100)
                    .max(Comparator.comparingDouble(this::contentScore))
                    .orElse(null);
            if (preferred != null) {
                return preferred;
            }
        }

        List<Element> semanticCandidates = document.select("main, article, [role=main]");
        Element semantic = semanticCandidates.stream()
                .filter(element -> element.text().length() >= 100)
                .max(Comparator.comparingDouble(this::contentScore))
                .orElse(null);
        if (semantic != null) {
            return semantic;
        }

        List<Element> candidates = new ArrayList<>();
        candidates.add(document.body());
        candidates.addAll(document.select("#main, #content, .main, .content, body > div, body > section"));
        return candidates.stream()
                .filter(element -> element != null && element.text().length() >= 80)
                .max(Comparator.comparingDouble(this::contentScore))
                .orElse(document.body());
    }

    private double contentScore(Element element) {
        int textLength = element.text().length();
        int linkLength = element.select("a").stream().mapToInt(link -> link.text().length()).sum();
        double linkDensity = textLength == 0 ? 1 : (double) linkLength / textLength;
        int paragraphCount = element.select("p").size();
        int headingCount = element.select("h1, h2, h3, h4").size();
        return textLength * (1 - Math.min(linkDensity, 0.95)) + paragraphCount * 80.0 + headingCount * 50.0;
    }

    private String renderText(Element source) {
        if (source == null) {
            return "";
        }
        return source.text().strip();
    }

    private UrlExtractionResult failed(String sourceUrl, String status, String message) {
        return new UrlExtractionResult(
                sourceUrl, sourceUrl, "", status, 0, 0, 0, 0, 0,
                false, "", "", List.of(), message
        );
    }

    private record FetchedPage(URI uri, String html) {
    }

    public record WebPageExtraction(
            UrlExtractionResult extraction,
            List<RenderedWebPageFetcher.RenderedFrame> frames
    ) {
    }
}
