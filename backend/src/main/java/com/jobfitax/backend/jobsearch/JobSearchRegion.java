package com.jobfitax.backend.jobsearch;

import java.util.List;

public record JobSearchRegion(
        String id,
        String name,
        List<String> saraminCodes,
        List<String> jobKoreaCodes,
        List<Area> areas
) {
    public record Area(
            String id,
            String name,
            List<String> saraminCodes,
            List<String> jobKoreaCodes
    ) {
    }
}
