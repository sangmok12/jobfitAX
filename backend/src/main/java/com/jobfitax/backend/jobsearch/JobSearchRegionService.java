package com.jobfitax.backend.jobsearch;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobSearchRegionService {

    private final List<JobSearchRegion> regions;
    private final Map<String, String> jobKoreaNames;

    public JobSearchRegionService(ObjectMapper objectMapper) {
        this.regions = loadRegions(objectMapper);
        Map<String, String> names = new LinkedHashMap<>();
        for (JobSearchRegion region : regions) {
            region.jobKoreaCodes().forEach(code -> names.put(code, region.name()));
            for (JobSearchRegion.Area area : region.areas()) {
                area.jobKoreaCodes().forEach(code -> names.put(code, region.name() + " " + area.name()));
            }
        }
        this.jobKoreaNames = Map.copyOf(names);
    }

    public List<JobSearchRegion> findAll() {
        return regions;
    }

    public RegionCodes resolve(List<String> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return new RegionCodes(List.of(), List.of());
        }

        Set<String> selected = Set.copyOf(selectedIds);
        Set<String> saramin = new LinkedHashSet<>();
        Set<String> jobKorea = new LinkedHashSet<>();

        for (JobSearchRegion region : regions) {
            if (selected.contains(region.id())) {
                saramin.addAll(region.saraminCodes());
                jobKorea.addAll(region.jobKoreaCodes());
                continue;
            }
            for (JobSearchRegion.Area area : region.areas()) {
                if (selected.contains(area.id())) {
                    saramin.addAll(area.saraminCodes());
                    jobKorea.addAll(area.jobKoreaCodes());
                }
            }
        }
        return new RegionCodes(List.copyOf(saramin), List.copyOf(jobKorea));
    }

    public List<String> jobKoreaNames(JsonNode codes) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode code : codes) {
            String value = code.asText();
            names.add(jobKoreaNames.getOrDefault(value, value));
        }
        return List.copyOf(names);
    }

    private List<JobSearchRegion> loadRegions(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("job-search/regions.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            List<JobSearchRegion> loaded = new ArrayList<>();
            for (JsonNode region : root) {
                List<JobSearchRegion.Area> areas = new ArrayList<>();
                for (JsonNode area : region.path("areas")) {
                    areas.add(new JobSearchRegion.Area(
                            area.path("id").asText(),
                            area.path("name").asText(),
                            strings(area.path("saraminCodes")),
                            strings(area.path("jobKoreaCodes"))
                    ));
                }
                loaded.add(new JobSearchRegion(
                        region.path("id").asText(),
                        region.path("name").asText(),
                        strings(region.path("saraminCodes")),
                        strings(region.path("jobKoreaCodes")),
                        List.copyOf(areas)
                ));
            }
            return List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("통합검색 지역 데이터를 읽지 못했습니다.", exception);
        }
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) values.add(value.asText());
        return List.copyOf(values);
    }

    public record RegionCodes(List<String> saraminCodes, List<String> jobKoreaCodes) {
    }
}
