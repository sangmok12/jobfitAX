package com.jobfitax.backend.analysis.source;

import java.io.IOException;
import java.util.List;

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
}
