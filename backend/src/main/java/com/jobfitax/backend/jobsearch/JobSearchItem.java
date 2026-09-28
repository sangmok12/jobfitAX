package com.jobfitax.backend.jobsearch;

import java.time.OffsetDateTime;
import java.util.List;

public record JobSearchItem(
        String source,
        String sourceLabel,
        String postingId,
        String companyName,
        String title,
        List<String> locations,
        String career,
        String education,
        List<String> employmentTypes,
        List<String> skills,
        String registeredDate,
        String deadline,
        String url,
        OffsetDateTime sortDate
) {
}
