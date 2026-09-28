package com.jobfitax.backend.analysis.job;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import com.jobfitax.backend.analysis.source.PublicUrlValidator;
import com.jobfitax.backend.analysis.source.RenderedWebPageFetcher.RenderedFrame;
import com.jobfitax.backend.analysis.source.TextNormalizer;
import com.jobfitax.backend.analysis.source.UrlExtractionResult;
import com.jobfitax.backend.analysis.source.WebPageTextExtractor.WebPageExtraction;

@Service
public class SaraminPageFetcher {

    private static final int MAX_RESPONSE_CHARACTERS = 3 * 1024 * 1024;
    private static final String USER_AGENT = "JobFitAX/1.0 (+job posting analysis)";
    private static final String SUMMARY_SELECTORS = String.join(", ",
            ".jv_header", ".jv_summary", ".jv_howto", ".jv_benefit", ".jv_location", ".jv_company");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final PublicUrlValidator urlValidator;
    private final TextNormalizer textNormalizer;

    public SaraminPageFetcher(PublicUrlValidator urlValidator, TextNormalizer textNormalizer) {
        this.urlValidator = urlValidator;
        this.textNormalizer = textNormalizer;
    }

    public WebPageExtraction fetch(String sourceUrl) {
        URI sourceUri = urlValidator.validate(sourceUrl);
        String recruitId = findQueryValue(sourceUri, "rec_idx");
        if (recruitId.isBlank() || !recruitId.matches("\\d+")) {
            throw new IllegalArgumentException("사람인 공고 번호를 URL에서 확인할 수 없습니다.");
        }

        URI ajaxUri = sourceUri.resolve("/zf_user/jobs/relay/view-ajax");
        String form = "rec_idx=" + encode(recruitId)
                + "&rec_seq=0&view_type=mail_landing&t_ref=non-logged_relay_view"
                + "&t_ref_content=category_new_rec";
        String ajaxHtml = send(HttpRequest.newBuilder(ajaxUri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Referer", sourceUri.toString())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());

        Document ajaxDocument = Jsoup.parse(ajaxHtml, ajaxUri.toString());
        String title = ajaxDocument.select(".jv_header .company, .jv_header .tit_job").text().strip();
        String rawText = ajaxDocument.body() == null ? "" : ajaxDocument.body().text().strip();
        Document summaryDocument = Jsoup.parse("<main></main>", ajaxUri.toString());
        Element summaryRoot = summaryDocument.selectFirst("main");
        ajaxDocument.select(SUMMARY_SELECTORS).forEach(element -> summaryRoot.appendChild(element.clone()));
        summaryRoot.select("script, style, noscript, template, svg, button, form, dialog, iframe, "
                + ".blind, .toolTip, .layer_map, .wrap_mapapi, .btn_more_cont, .jv_title").remove();
        String summaryText = textNormalizer.normalize(summaryRoot.text()).text();
        if (summaryText.length() < 50) {
            throw new IllegalArgumentException("사람인 공고의 기본 정보를 충분히 찾지 못했습니다.");
        }

        List<RenderedFrame> frames = new ArrayList<>();
        Element detailFrame = ajaxDocument.selectFirst("iframe#iframe_content_0[src], iframe[title=상세요강][src]");
        if (detailFrame != null) {
            URI detailUri = urlValidator.validate(detailFrame.absUrl("src"));
            String detailHtml = send(HttpRequest.newBuilder(detailUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", sourceUri.toString())
                    .GET().build());
            frames.add(new RenderedFrame(detailUri, detailHtml));
        }

        UrlExtractionResult extraction = new UrlExtractionResult(
                sourceUrl, sourceUri.toString(), title, "SUCCESS",
                rawText.length(), summaryText.length(), summaryText.length(),
                Math.max(0, rawText.length() - summaryText.length()), 0, false,
                rawText, summaryText, List.of(), null
        );
        return new WebPageExtraction(extraction, List.copyOf(frames));
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("사람인 공고 내용을 가져오지 못했습니다.");
            }
            if (response.body().length() > MAX_RESPONSE_CHARACTERS) {
                throw new IllegalArgumentException("사람인 공고 페이지 크기가 제한을 초과했습니다.");
            }
            return response.body();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("사람인 공고 내용을 가져오지 못했습니다.", exception);
        }
    }

    private String findQueryValue(URI uri, String name) {
        if (uri.getRawQuery() == null) {
            return "";
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(name)) {
                return parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        }
        return "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
