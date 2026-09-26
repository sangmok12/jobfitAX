package com.jobfitax.backend.analysis.source;

import java.util.List;

public record NormalizationResult(
        String text,
        int removedCharacterCount,
        List<String> appliedRules
) {
}
