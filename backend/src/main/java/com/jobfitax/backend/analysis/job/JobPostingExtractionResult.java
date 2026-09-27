package com.jobfitax.backend.analysis.job;

import com.jobfitax.backend.analysis.source.UrlExtractionResult;

public record JobPostingExtractionResult(
        String siteCode,
        String siteName,
        boolean officiallySupported,
        UrlExtractionResult extraction,
        JobPostingOcrResult ocr
) {
}
