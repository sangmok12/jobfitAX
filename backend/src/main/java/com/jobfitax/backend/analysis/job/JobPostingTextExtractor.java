package com.jobfitax.backend.analysis.job;

import org.springframework.stereotype.Service;

import com.jobfitax.backend.analysis.source.UrlExtractionResult;
import com.jobfitax.backend.analysis.source.WebPageTextExtractor;

@Service
public class JobPostingTextExtractor {

    private static final int MAX_ANALYSIS_CHARACTERS = 50_000;

    private final WebPageTextExtractor webPageTextExtractor;
    private final JobKoreaTextCleaner jobKoreaTextCleaner;
    private final JobKoreaImageOcrService jobKoreaImageOcrService;
    private final SaraminTextCleaner saraminTextCleaner;
    private final SaraminDetailExtractor saraminDetailExtractor;
    private final SaraminPageFetcher saraminPageFetcher;

    public JobPostingTextExtractor(
            WebPageTextExtractor webPageTextExtractor,
            JobKoreaTextCleaner jobKoreaTextCleaner,
            JobKoreaImageOcrService jobKoreaImageOcrService,
            SaraminTextCleaner saraminTextCleaner,
            SaraminDetailExtractor saraminDetailExtractor,
            SaraminPageFetcher saraminPageFetcher
    ) {
        this.webPageTextExtractor = webPageTextExtractor;
        this.jobKoreaTextCleaner = jobKoreaTextCleaner;
        this.jobKoreaImageOcrService = jobKoreaImageOcrService;
        this.saraminTextCleaner = saraminTextCleaner;
        this.saraminDetailExtractor = saraminDetailExtractor;
        this.saraminPageFetcher = saraminPageFetcher;
    }

    public JobPostingExtractionResult extract(String url) {
        JobPostingSite site = JobPostingSite.fromUrl(url);
        WebPageTextExtractor.WebPageExtraction webPageExtraction = site == JobPostingSite.SARAMIN
                ? saraminPageFetcher.fetch(url)
                : webPageTextExtractor.extractDetailed(url, site.preferredContentSelectors());
        UrlExtractionResult extraction = webPageExtraction.extraction();
        JobPostingOcrResult ocr = JobPostingOcrResult.noImage();
        if (site == JobPostingSite.JOBKOREA) {
            extraction = jobKoreaTextCleaner.clean(extraction);
            ocr = jobKoreaImageOcrService.extract(webPageExtraction.frames());
            extraction = mergeOcrText(extraction, ocr);
        } else if (site == JobPostingSite.SARAMIN) {
            extraction = saraminTextCleaner.clean(extraction);
            ocr = saraminDetailExtractor.extract(webPageExtraction.frames());
            extraction = mergeOcrText(extraction, ocr);
        }
        return new JobPostingExtractionResult(
                site.code(),
                site.displayName(),
                site.officiallySupported(),
                extraction,
                ocr
        );
    }

    private UrlExtractionResult mergeOcrText(UrlExtractionResult source, JobPostingOcrResult ocr) {
        if (!"SUCCESS".equals(source.status())
                || !("SUCCESS".equals(ocr.status()) || "FRAME_TEXT".equals(ocr.status()))) {
            return source;
        }

        boolean hasUsefulOuterText = source.normalizedText().length() >= 80
                && !source.normalizedText().contains("이어보는 Ai매치 채용정보");
        String outerText = hasUsefulOuterText
                ? "[HTML에서 추출한 채용 정보]\n" + source.normalizedText() + "\n\n"
                : "";
        String combined = outerText
                + ("FRAME_TEXT".equals(ocr.status())
                        ? "[상세공고 HTML 본문]\n"
                        : "[상세공고 이미지 OCR]\n")
                + ocr.text();
        String combinedRaw = (hasUsefulOuterText ? source.rawText() : "")
                + ("FRAME_TEXT".equals(ocr.status())
                        ? "\n\n[상세공고 HTML 본문]\n"
                        : "\n\n[상세공고 이미지 OCR]\n")
                + ocr.text();
        boolean truncated = combined.length() > MAX_ANALYSIS_CHARACTERS;
        String analysisText = truncated ? combined.substring(0, MAX_ANALYSIS_CHARACTERS) : combined;

        return new UrlExtractionResult(
                source.sourceUrl(),
                source.finalUrl(),
                source.pageTitle(),
                source.status(),
                combinedRaw.length(),
                combined.length(),
                analysisText.length(),
                Math.max(0, combinedRaw.length() - combined.length()),
                Math.max(0, combined.length() - analysisText.length()),
                truncated,
                combinedRaw,
                analysisText,
                source.removalStats(),
                source.errorMessage()
        );
    }
}
