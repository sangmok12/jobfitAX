package com.jobfitax.backend.analysis;

import java.util.List;

public record AnalysisRequestResponse(
        String status,
        String jobPostingUrl,
        List<String> receivedFiles
) {
}
