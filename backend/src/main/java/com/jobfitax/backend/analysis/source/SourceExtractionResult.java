package com.jobfitax.backend.analysis.source;

import java.util.List;

public record SourceExtractionResult(
        String sourceName,
        String status,
        int rawCharacterCount,
        int cleanedCharacterCount,
        int analysisCharacterCount,
        int removedCharacterCount,
        int truncatedCharacterCount,
        boolean truncated,
        String rawText,
        String normalizedText,
        List<String> appliedRules,
        String errorMessage
) {
}
