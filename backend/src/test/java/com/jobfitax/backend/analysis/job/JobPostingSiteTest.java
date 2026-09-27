package com.jobfitax.backend.analysis.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobPostingSiteTest {

    @Test
    void classifiesOfficialDomainsAndSubdomains() {
        assertThat(JobPostingSite.fromUrl("https://www.jobkorea.co.kr/Recruit/GI_Read/1"))
                .isEqualTo(JobPostingSite.JOBKOREA);
        assertThat(JobPostingSite.fromUrl("https://m.saramin.co.kr/job-search/view"))
                .isEqualTo(JobPostingSite.SARAMIN);
        assertThat(JobPostingSite.fromUrl("https://jumpit.saramin.co.kr/position/1"))
                .isEqualTo(JobPostingSite.JUMPIT);
        assertThat(JobPostingSite.fromUrl("https://www.wanted.co.kr/wd/1"))
                .isEqualTo(JobPostingSite.WANTED);
    }

    @Test
    void doesNotTrustDomainNamesThatOnlyContainTheBrandName() {
        assertThat(JobPostingSite.fromUrl("https://jobkorea-fake.com/jobs/1"))
                .isEqualTo(JobPostingSite.OTHER);
        assertThat(JobPostingSite.fromUrl("https://example.com/jobkorea/jobs/1"))
                .isEqualTo(JobPostingSite.OTHER);
    }
}
