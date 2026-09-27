package com.jobfitax.backend.analysis.job;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public enum JobPostingSite {
    JOBKOREA("JOBKOREA", "잡코리아", "jobkorea.co.kr",
            List.of(".recruit-view", ".view-content", "#content", "main")),
    SARAMIN("SARAMIN", "사람인", "saramin.co.kr",
            List.of(".wrap_jview", ".jv_cont", "#content", "main")),
    JUMPIT("JUMPIT", "점핏", "jumpit.saramin.co.kr",
            List.of("main", "#content")),
    WANTED("WANTED", "원티드", "wanted.co.kr",
            List.of("main", "article", "#content")),
    OTHER("OTHER", "기타 공개 채용공고", "", List.of("main", "article", "[role=main]"));

    private final String code;
    private final String displayName;
    private final String domain;
    private final List<String> preferredContentSelectors;

    JobPostingSite(String code, String displayName, String domain, List<String> preferredContentSelectors) {
        this.code = code;
        this.displayName = displayName;
        this.domain = domain;
        this.preferredContentSelectors = preferredContentSelectors;
    }

    public static JobPostingSite fromUrl(String url) {
        String host = URI.create(url).getHost();
        if (host == null) {
            return OTHER;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);

        // 점핏은 사람인의 하위 도메인이므로 사람인보다 먼저 확인한다.
        for (JobPostingSite site : List.of(JUMPIT, JOBKOREA, SARAMIN, WANTED)) {
            if (normalizedHost.equals(site.domain) || normalizedHost.endsWith("." + site.domain)) {
                return site;
            }
        }
        return OTHER;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public boolean officiallySupported() {
        return this != OTHER;
    }

    public List<String> preferredContentSelectors() {
        return preferredContentSelectors;
    }
}
