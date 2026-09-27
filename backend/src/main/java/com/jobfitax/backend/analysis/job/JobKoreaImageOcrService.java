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
public class JobKoreaImageOcrService {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_DETAIL_IMAGES = 3;
    private static final int MIN_IMAGE_WIDTH = 600;
    private static final int MIN_IMAGE_HEIGHT = 600;
    private static final List<String> JOB_RELEVANT_TERMS = List.of(
            "업무", "자격", "근무", "지원", "채용", "입출고", "전산", "현장",
            "계약", "운전", "지게차", "학력", "경력", "우대", "급여", "복리",
            "보험", "휴가", "검진", "전환", "소지자", "상하차", "분류"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final PublicUrlValidator urlValidator;
    private final TesseractOcrEngine ocrEngine;
    private final TextNormalizer textNormalizer;

    public JobKoreaImageOcrService(
            PublicUrlValidator urlValidator,
            TesseractOcrEngine ocrEngine,
            TextNormalizer textNormalizer
    ) {
        this.urlValidator = urlValidator;
        this.ocrEngine = ocrEngine;
        this.textNormalizer = textNormalizer;
    }

    public JobPostingOcrResult extract(List<RenderedFrame> frames) {
        Set<String> imageUrls = findImageUrls(frames);
        String frameText = extractFrameText(frames);
        if (isUsefulFrameText(frameText)) {
            return new JobPostingOcrResult(
                    "FRAME_TEXT",
                    imageUrls.size(),
                    0,
                    frameText.length(),
                    frameText,
                    "상세공고가 HTML 텍스트로 제공되어 OCR 없이 정확한 내용을 포함했습니다."
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
                temporaryImage = Files.createTempFile("jobfit-job-image-", ".png");
                Files.write(temporaryImage, bytes);
                String normalized = cleanOcrText(textNormalizer.normalize(ocrEngine.recognize(temporaryImage)).text());
                if (isUsefulOcrText(normalized)) {
                    acceptedTexts.add(normalized);
                }
            } catch (Exception ignored) {
                // 개별 이미지 오류가 전체 채용공고 분석을 중단시키지 않도록 다음 이미지를 확인한다.
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
                    "상세공고 이미지를 찾았지만 충분한 글자를 인식하지 못했습니다."
            );
        }

        String text = String.join("\n\n", acceptedTexts);
        return new JobPostingOcrResult(
                "SUCCESS", detailImageCount, acceptedTexts.size(), text.length(), text,
                "상세공고 이미지의 글자를 추출해 AI 전달 내용에 포함했습니다."
        );
    }

    private Set<String> findImageUrls(List<RenderedFrame> frames) {
        Set<String> urls = new LinkedHashSet<>();
        for (RenderedFrame frame : frames) {
            Document document = Jsoup.parse(frame.html(), frame.uri().toString());
            for (Element image : document.select("img[src]")) {
                String absoluteUrl = image.absUrl("src");
                if (!absoluteUrl.isBlank()) {
                    urls.add(absoluteUrl);
                }
            }
        }
        return urls;
    }

    private String extractFrameText(List<RenderedFrame> frames) {
        List<String> texts = new ArrayList<>();
        for (RenderedFrame frame : frames) {
            Document document = Jsoup.parse(frame.html(), frame.uri().toString());
            document.select("script, style, noscript, template, iframe, form").remove();
            if (document.body() == null) {
                continue;
            }
            String text = textNormalizer.normalize(document.body().wholeText()).text();
            text = cutAtFirst(text, List.of(
                    "인사 담당자 연락처",
                    "개인정보 처리방침",
                    "본 채용정보는"
            ));
            if (!text.isBlank()) {
                texts.add(text);
            }
        }
        return String.join("\n\n", texts).strip().replaceFirst("[\\s◎˚̊•]+$", "");
    }

    private boolean isUsefulFrameText(String text) {
        if (text.length() < 80) {
            return false;
        }
        long matchedTerms = JOB_RELEVANT_TERMS.stream().filter(text::contains).count();
        return matchedTerms >= 3;
    }

    private byte[] download(String imageUrl) throws IOException, InterruptedException {
        URI current = urlValidator.validate(imageUrl);
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "JobFitAX/1.0 (+job image OCR)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("이미지 리다이렉트 주소가 없습니다."));
                current = urlValidator.validate(current.resolve(location).toString());
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("이미지를 가져오지 못했습니다.");
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.startsWith("image/") || response.body().length > MAX_IMAGE_BYTES) {
                throw new IOException("OCR 처리 대상 이미지가 아닙니다.");
            }
            return response.body();
        }
        throw new IOException("이미지 리다이렉트 횟수가 너무 많습니다.");
    }

    private boolean isDetailImage(BufferedImage image) {
        return image != null
                && image.getWidth() >= MIN_IMAGE_WIDTH
                && image.getHeight() >= MIN_IMAGE_HEIGHT
                && image.getHeight() >= image.getWidth();
    }

    private boolean isUsefulOcrText(String text) {
        if (text.length() < 80) {
            return false;
        }
        long meaningful = text.codePoints()
                .filter(codePoint -> Character.isLetterOrDigit(codePoint) || Character.isWhitespace(codePoint))
                .count();
        return (double) meaningful / text.codePointCount(0, text.length()) >= 0.65;
    }

    String cleanOcrText(String source) {
        String text = cutAtFirst(source, List.of(
                "유의사항",
                "개인정보 처리방침",
                "채용서류 반환"
        ));

        Set<String> uniqueLines = new LinkedHashSet<>();
        for (String sourceLine : text.lines().toList()) {
            String line = sourceLine.replace('ᆞ', '•').strip()
                    .replaceFirst("^[+•]\\s*", "")
                    .replaceFirst("^[A-Z]{1,5}(?:\\s+[A-Z]{1,5})?\\s+(?=[가-힣])", "");
            if (isUsefulLine(line)) {
                uniqueLines.add(line);
            }
        }
        return String.join("\n", uniqueLines).strip();
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

    private boolean isUsefulLine(String line) {
        if (line.length() < 3) {
            return false;
        }
        long korean = line.codePoints()
                .filter(codePoint -> codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                .count();
        long lettersAndNumbers = line.codePoints().filter(Character::isLetterOrDigit).count();
        double readableRatio = (double) lettersAndNumbers / line.codePointCount(0, line.length());
        if (readableRatio < 0.45) {
            return false;
        }
        if (line.toLowerCase().contains("coupang logistics services")) {
            return true;
        }
        boolean containsJobTerm = JOB_RELEVANT_TERMS.stream().anyMatch(line::contains);
        double koreanRatio = lettersAndNumbers == 0 ? 0 : (double) korean / lettersAndNumbers;
        return containsJobTerm || (line.length() >= 12 && koreanRatio >= 0.72);
    }
}
