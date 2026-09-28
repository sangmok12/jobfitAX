package com.jobfitax.backend.analysis;

import java.util.List;

public record JobFitAnalysisRequest(
        String jobPostingUrl,
        String jobPostingSite,
        String jobPostingText,
        String preferences,
        List<UserMaterial> userMaterials
) {
    public record UserMaterial(String name, String text) {
    }
}
