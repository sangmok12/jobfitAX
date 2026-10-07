package com.jobfitax.backend.analysis.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentTextExtractor {

    private static final int MAX_EXTRACTED_CHARACTERS = 200_000;
    private static final int MAX_ANALYSIS_CHARACTERS = 50_000;
    private static final int MAX_RAW_PREVIEW_CHARACTERS = 100_000;

    private final Tika tika;
    private final TextNormalizer textNormalizer;

    public DocumentTextExtractor(TextNormalizer textNormalizer) {
        this.textNormalizer = textNormalizer;
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_EXTRACTED_CHARACTERS);
    }

    public SourceExtractionResult extract(MultipartFile file) {
        String sourceName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "이름 없는 파일" : file.getOriginalFilename()
        );

        try {
            String invalidPdfMessage = detectHtmlSavedAsPdf(file, sourceName);
            if (invalidPdfMessage != null) {
                return invalidFileResult(sourceName, invalidPdfMessage);
            }

            String rawText = tika.parseToString(file.getInputStream());
            NormalizationResult normalization = textNormalizer.normalize(rawText);

            if (!StringUtils.hasText(normalization.text())) {
                return emptyResult(sourceName, rawText);
            }

            boolean truncated = normalization.text().length() > MAX_ANALYSIS_CHARACTERS;
            String analysisText = truncated
                    ? normalization.text().substring(0, MAX_ANALYSIS_CHARACTERS)
                    : normalization.text();
            String rawPreview = rawText.length() > MAX_RAW_PREVIEW_CHARACTERS
                    ? rawText.substring(0, MAX_RAW_PREVIEW_CHARACTERS)
                    : rawText;

            return new SourceExtractionResult(
                    sourceName,
                    "SUCCESS",
                    rawText.length(),
                    normalization.text().length(),
                    analysisText.length(),
                    normalization.removedCharacterCount(),
                    Math.max(0, normalization.text().length() - analysisText.length()),
                    truncated,
                    rawPreview,
                    analysisText,
                    normalization.appliedRules(),
                    null
            );
        } catch (Exception exception) {
            return failedResult(sourceName, exception);
        }
    }

    private String detectHtmlSavedAsPdf(MultipartFile file, String sourceName) throws IOException {
        if (!sourceName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return null;
        }

        byte[] prefix;
        try (InputStream inputStream = file.getInputStream()) {
            prefix = inputStream.readNBytes(4_096);
        }

        String content = new String(prefix, StandardCharsets.UTF_8).stripLeading();
        String lowerContent = content.toLowerCase(Locale.ROOT);
        boolean isHtml = lowerContent.startsWith("<!doctype html")
                || lowerContent.startsWith("<html")
                || lowerContent.contains("<body")
                || lowerContent.contains("<script");

        if (!isHtml) {
            return null;
        }

        if (content.contains("세션이 만료") || lowerContent.contains("login_tot.asp")) {
            return "PDF가 아니라 로그인 세션 만료 페이지가 저장되었습니다. "
                    + "잡코리아에 다시 로그인한 뒤 이력서를 PDF로 다시 내려받아 주세요.";
        }

        return "PDF가 아니라 웹페이지 HTML이 저장되었습니다. 원본 사이트에서 PDF를 다시 내려받아 주세요.";
    }

    private SourceExtractionResult emptyResult(String sourceName, String rawText) {
        return new SourceExtractionResult(
                sourceName, "NO_TEXT", rawText.length(), 0, 0, rawText.length(), 0, false,
                rawText, "", List.of(), "텍스트를 찾을 수 없습니다. 스캔 문서는 OCR이 필요할 수 있습니다."
        );
    }

    private SourceExtractionResult failedResult(String sourceName, Exception exception) {
        String message = exception instanceof IOException
                ? "파일을 읽을 수 없습니다."
                : "파일에서 텍스트를 추출하지 못했습니다.";
        return new SourceExtractionResult(
                sourceName, "FAILED", 0, 0, 0, 0, 0, false,
                "", "", List.of(), message
        );
    }

    private SourceExtractionResult invalidFileResult(String sourceName, String message) {
        return new SourceExtractionResult(
                sourceName, "FAILED", 0, 0, 0, 0, 0, false,
                "", "", List.of(), message
        );
    }
}
