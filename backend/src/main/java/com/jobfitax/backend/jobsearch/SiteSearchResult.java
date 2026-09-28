package com.jobfitax.backend.jobsearch;

import java.util.List;

record SiteSearchResult(
        String source,
        String sourceLabel,
        List<JobSearchItem> items,
        int totalAvailable,
        boolean limited,
        String errorMessage
) {
    static SiteSearchResult failed(String source, String sourceLabel, String errorMessage) {
        return new SiteSearchResult(source, sourceLabel, List.of(), 0, false, errorMessage);
    }
}
