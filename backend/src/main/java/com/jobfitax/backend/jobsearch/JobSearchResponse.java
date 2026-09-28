package com.jobfitax.backend.jobsearch;

import java.util.List;

public record JobSearchResponse(
        List<JobSearchItem> items,
        int totalReturned,
        int maximumResults,
        boolean limited,
        List<SiteResult> sites
) {
    public record SiteResult(
            String source,
            String sourceLabel,
            String status,
            int returnedCount,
            int totalAvailable,
            boolean limited,
            String message
    ) {
    }
}
