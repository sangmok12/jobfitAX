package com.jobfitax.backend.jobsearch;

import java.util.List;

public record JobSearchRequest(
        String keyword,
        List<String> regionIds,
        String careerType,
        Integer minimumCareerYears,
        Integer maximumCareerYears,
        String minimumEducation,
        List<String> employmentTypes
) {
}
