package com.jobfitax.backend.jobsearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.jobfitax.backend.jobsearch.JobSearchRegionService.RegionCodes;
import com.jobfitax.backend.jobsearch.JobSearchResponse.SiteResult;

@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);
    private static final int MAXIMUM_RESULTS = 500;

    private final JobSearchRegionService regionService;
    private final JobKoreaSearchClient jobKoreaClient;
    private final SaraminSearchClient saraminClient;

    public JobSearchService(
            JobSearchRegionService regionService,
            JobKoreaSearchClient jobKoreaClient,
            SaraminSearchClient saraminClient
    ) {
        this.regionService = regionService;
        this.jobKoreaClient = jobKoreaClient;
        this.saraminClient = saraminClient;
    }

    public JobSearchResponse search(JobSearchRequest request) {
        validate(request);
        RegionCodes regions = regionService.resolve(request.regionIds());

        CompletableFuture<SiteSearchResult> jobKorea = CompletableFuture.supplyAsync(
                () -> searchSafely("JOBKOREA", "잡코리아",
                        () -> jobKoreaClient.search(request, regions.jobKoreaCodes())));
        CompletableFuture<SiteSearchResult> saramin = CompletableFuture.supplyAsync(
                () -> searchSafely("SARAMIN", "사람인",
                        () -> saraminClient.search(request, regions.saraminCodes())));

        List<SiteSearchResult> siteResults = List.of(jobKorea.join(), saramin.join());
        List<JobSearchItem> items = siteResults.stream()
                .flatMap(result -> result.items().stream())
                .sorted(Comparator.comparing(JobSearchItem::sortDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAXIMUM_RESULTS)
                .toList();
        boolean limited = siteResults.stream().anyMatch(SiteSearchResult::limited)
                || siteResults.stream().mapToInt(result -> result.items().size()).sum() > MAXIMUM_RESULTS;

        List<SiteResult> sites = siteResults.stream().map(result -> new SiteResult(
                result.source(), result.sourceLabel(),
                StringUtils.hasText(result.errorMessage()) ? "FAILED" : "SUCCESS",
                result.items().size(), result.totalAvailable(), result.limited(), result.errorMessage()
        )).toList();

        if (sites.stream().allMatch(site -> "FAILED".equals(site.status()))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "잡코리아와 사람인 검색에 모두 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return new JobSearchResponse(items, items.size(), MAXIMUM_RESULTS, limited, sites);
    }

    private SiteSearchResult searchSafely(
            String source,
            String label,
            java.util.function.Supplier<SiteSearchResult> search
    ) {
        try {
            return search.get();
        } catch (RuntimeException exception) {
            log.warn("{} 통합검색 실패: {}", label, exception.getMessage());
            return SiteSearchResult.failed(source, label, label + " 검색 결과를 가져오지 못했습니다.");
        }
    }

    private void validate(JobSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.keyword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해 주세요.");
        }
        if (request.keyword().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 100자 이하로 입력해 주세요.");
        }
        Integer minimum = request.minimumCareerYears();
        Integer maximum = request.maximumCareerYears();
        if ((minimum != null && (minimum < 0 || minimum > 30))
                || (maximum != null && (maximum < 0 || maximum > 30))
                || (minimum != null && maximum != null && minimum > maximum)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "경력 범위를 올바르게 선택해 주세요.");
        }
    }
}
