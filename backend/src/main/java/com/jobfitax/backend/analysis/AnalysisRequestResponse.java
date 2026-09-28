package com.jobfitax.backend.analysis;

import java.util.List;

import com.jobfitax.backend.analysis.job.JobPostingExtractionResult;
import com.jobfitax.backend.analysis.source.SourceExtractionResult;
import com.jobfitax.backend.analysis.source.UrlExtractionResult;

public record AnalysisRequestResponse(
        String status,
        String jobPostingUrl,
        JobPostingExtractionResult jobPosting,
        String preferences,
        List<String> receivedFiles,
        List<String> receivedUrls,
        List<SourceExtractionResult> extractedSources,
        List<UrlExtractionResult> extractedUrls
) {
}
