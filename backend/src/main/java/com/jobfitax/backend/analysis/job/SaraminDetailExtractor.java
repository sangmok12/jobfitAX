package com.jobfitax.backend.analysis.job;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import com.jobfitax.backend.analysis.source.PublicUrlValidator;
import com.jobfitax.backend.analysis.source.RenderedWebPageFetcher.RenderedFrame;
import com.jobfitax.backend.analysis.source.TextNormalizer;

@Service
public class SaraminDetailExtractor {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_DETAIL_IMAGES = 4;
    private static final int MIN_IMAGE_WIDTH = 550;
    private static final int MIN_IMAGE_HEIGHT = 500;
    private static final List<String> JOB_TERMS = List.of(
            "담당업무", "주요업무", "자격요건", "지원자격", "우대사항", "근무조건",
            "근무지역", "근무형태", "급여", "연봉", "복리후생", "채용절차", "전형절차",
            "접수기간", "경력", "학력", "채용", "모집", "지원"
    );
    private static final List<String> TEXT_CUT_MARKERS = List.of(
            "본 채용정보는", "복리후생을 확인해보세요", "기업정보 더보기",
            "이 공고와 함께 본 공고", "인사담당자 연락처", "개인정보 처리방침",
            "유의사항", "채용서류 반환"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final PublicUrlValidator urlValidator;
    private final TesseractOcrEngine ocrEngine;
    private final TextNormalizer textNormalizer;

    public SaraminDetailExtractor(
            PublicUrlValidator urlValidator,
            TesseractOcrEngine ocrEngine,
            TextNormalizer textNormalizer
    ) {
        this.urlValidator = urlValidator;
        this.ocrEngine = ocrEngine;
        this.textNormalizer = textNormalizer;
    }

    public JobPostingOcrResult extract(List<RenderedFrame> frames) {
        List<FrameContent> candidates = frames.stream()
                .map(this::readFrame)
                .filter(content -> content.relevanceScore() > 0 || !content.imageUrls().isEmpty())
                .toList();

        String frameText = candidates.stream()
                .filter(content -> isUsefulText(content.text()))
                .max(Comparator.comparingInt(FrameContent::relevanceScore)
                        .thenComparingInt(content -> content.text().length()))
                .map(FrameContent::text)
                .orElse("");
        Set<String> imageUrls = new LinkedHashSet<>();
        candidates.forEach(content -> imageUrls.addAll(content.imageUrls()));

        if (isUsefulText(frameText)) {
            return new JobPostingOcrResult(
                    "FRAME_TEXT", imageUrls.size(), 0, frameText.length(), frameText,
                    "사람인 상세공고가 HTML 텍스트로 제공되어 OCR 없이 정확한 내용을 포함했습니다."
            );
        }
        if (imageUrls.isEmpty()) {
            return JobPostingOcrResult.noImage();
        }

        List<String> acceptedTexts = new ArrayList<>();
        int detailImageCount = 0;
        for (String imageUrl : imageUrls) {
            if (detailImageCount >= MAX_DETAIL_IMAGES) {
                break;
            }
            Path temporaryImage = null;
            try {
                byte[] bytes = download(imageUrl);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (!isDetailImage(image)) {
                    continue;
                }
                detailImageCount++;
                temporaryImage = Files.createTempFile("jobfit-saramin-image-", ".png");
                Files.write(temporaryImage, bytes);
                String text = cleanOcrText(textNormalizer.normalize(ocrEngine.recognize(temporaryImage)).text());
                if (isUsefulOcrText(text)) {
                    acceptedTexts.add(text);
                }
            } catch (Exception ignored) {
                // 한 이미지의 실패가 전체 공고 분석을 중단시키지 않도록 다음 이미지를 확인한다.
            } finally {
                if (temporaryImage != null) {
                    try {
                        Files.deleteIfExists(temporaryImage);
                    } catch (IOException ignored) {
                        // 임시 파일 정리 실패는 분석 결과에 영향을 주지 않는다.
                    }
                }
            }
        }

        if (detailImageCount == 0) {
            return JobPostingOcrResult.noImage();
        }
        if (acceptedTexts.isEmpty()) {
            return new JobPostingOcrResult(
                    "LOW_QUALITY", detailImageCount, 0, 0, "",
                    "사람인 상세공고 이미지를 찾았지만 충분한 글자를 인식하지 못했습니다."
            );
        }
        String text = String.join("\n\n", acceptedTexts);
        return new JobPostingOcrResult(
                "SUCCESS", detailImageCount, acceptedTexts.size(), text.length(), text,
                "사람인 상세공고 이미지의 글자를 추출해 AI 전달 내용에 포함했습니다."
        );
    }

    private FrameContent readFrame(RenderedFrame frame) {
        Document document = Jsoup.parse(frame.html(), frame.uri().toString());
        Set<String> imageUrls = findImageUrls(document);
        document.select("script, style, noscript, template, iframe, form, nav, header, footer").remove();
        String text = document.body() == null ? "" : textNormalizer.normalize(document.body().wholeText()).text();
        text = cutAtFirst(text, TEXT_CUT_MARKERS).strip();
        int matchedTerms = (int) JOB_TERMS.stream().filter(text::contains).count();
        int score = matchedTerms * 1_000 + Math.min(text.length(), 20_000);
        return new FrameContent(text, imageUrls, matchedTerms >= 2 ? score : 0);
    }

    private Set<String> findImageUrls(Document document) {
        Set<String> urls = new LinkedHashSet<>();
        for (Element image : document.select("img")) {
            for (String attribute : List.of("src", "data-src", "data-original", "data-lazy-src")) {
                String value = image.attr(attribute);
                if (value.isBlank() || value.startsWith("data:")) {
                    continue;
                }
                try {
                    URI resolved = URI.create(document.baseUri()).resolve(value);
                    if ("http".equalsIgnoreCase(resolved.getScheme()) || "https".equalsIgnoreCase(resolved.getScheme())) {
                        urls.add(resolved.toString());
                    }
                } catch (IllegalArgumentException ignored) {
                    // 올바르지 않은 이미지 주소는 제외한다.
                }
            }
        }
        return urls;
    }

    private boolean isUsefulText(String text) {
        if (text.length() < 100) {
            return false;
        }
        return JOB_TERMS.stream().filter(text::contains).count() >= 3;
    }

    private byte[] download(String imageUrl) throws IOException, InterruptedException {
        URI current = urlValidator.validate(imageUrl);
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 JobFitAX/1.0")
                    .header("Referer", "https://www.saramin.co.kr/")
                    .GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("이미지 리다이렉트 주소가 없습니다."));
                current = urlValidator.validate(current.resolve(location).toString());
                continue;
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || !contentType.startsWith("image/") || response.body().length > MAX_IMAGE_BYTES) {
                throw new IOException("OCR 처리 대상 이미지를 가져오지 못했습니다.");
            }
            return response.body();
        }
        throw new IOException("이미지 리다이렉트 횟수가 너무 많습니다.");
    }

    private boolean isDetailImage(BufferedImage image) {
        return image != null && image.getWidth() >= MIN_IMAGE_WIDTH
                && image.getHeight() >= MIN_IMAGE_HEIGHT
                && image.getHeight() >= image.getWidth() * 0.7;
    }

    private boolean isUsefulOcrText(String text) {
        if (text.length() < 80) {
            return false;
        }
        long meaningful = text.codePoints()
                .filter(codePoint -> Character.isLetterOrDigit(codePoint) || Character.isWhitespace(codePoint))
                .count();
        return (double) meaningful / text.codePointCount(0, text.length()) >= 0.62;
    }

    String cleanOcrText(String source) {
        String text = cutAtFirst(source, TEXT_CUT_MARKERS);
        Set<String> uniqueLines = new LinkedHashSet<>();
        for (String sourceLine : text.lines().toList()) {
            String line = sourceLine.replace('ᆞ', '•').strip()
                    .replaceFirst("^[+•]\\s*", "")
                    .replaceFirst("^[A-Z]{1,6}(?:\\s+[A-Z]{1,6})?\\s+(?=[가-힣])", "");
            if (isUsefulLine(line)) {
                uniqueLines.add(line);
            }
        }
        return String.join("\n", uniqueLines).strip();
    }

    private boolean isUsefulLine(String line) {
        if (line.length() < 3) {
            return false;
        }
        long korean = line.codePoints().filter(codePoint -> codePoint >= 0xAC00 && codePoint <= 0xD7A3).count();
        long lettersAndNumbers = line.codePoints().filter(Character::isLetterOrDigit).count();
        double readableRatio = (double) lettersAndNumbers / line.codePointCount(0, line.length());
        boolean containsJobTerm = JOB_TERMS.stream().anyMatch(line::contains);
        double koreanRatio = lettersAndNumbers == 0 ? 0 : (double) korean / lettersAndNumbers;
        return readableRatio >= 0.42 && (containsJobTerm || (line.length() >= 10 && koreanRatio >= 0.65));
    }

    private String cutAtFirst(String text, List<String> markers) {
        int cutIndex = text.length();
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0) {
                cutIndex = Math.min(cutIndex, index);
            }
        }
        return text.substring(0, cutIndex);
    }

    private record FrameContent(String text, Set<String> imageUrls, int relevanceScore) {
    }
}
