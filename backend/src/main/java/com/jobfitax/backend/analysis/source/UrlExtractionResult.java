package com.jobfitax.backend.analysis.source;

import java.util.List;

public record UrlExtractionResult(
        String sourceUrl,
        String finalUrl,
        String pageTitle,
        String status,
        int rawCharacterCount,
        int cleanedCharacterCount,
        int analysisCharacterCount,
        int excludedCharacterCount,
        int truncatedCharacterCount,
        boolean truncated,
        String rawText,
        String normalizedText,
        List<RemovalStat> removalStats,
        String errorMessage
) {
}
