package com.jobfitax.backend.analysis;

import java.util.List;

public record JobFitAnalysisResult(
        String companyName,
        String position,
        Integer overallScore,
        String overallStatus,
        String summary,
        Assessment skill,
        Assessment experience,
        Assessment role,
        Assessment preference,
        List<Insight> strengths,
        List<Insight> gaps,
        List<String> applicationTips,
        List<String> preparations,
        JobInformation jobInformation,
        List<String> dataLimitations,
        Usage usage
) {
    public record Assessment(
            String status,
            Integer score,
            String rationale,
            List<String> evidence
    ) {
    }

    public record Insight(
            String title,
            String description,
            String userEvidence,
            String jobEvidence
    ) {
    }

    public record JobInformation(
            List<String> responsibilities,
            List<String> requiredQualifications,
            List<String> preferredQualifications,
            List<String> employmentConditions,
            List<String> unknownInformation
    ) {
    }

    public record Usage(
            String model,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            double estimatedCostUsd
    ) {
    }
}
