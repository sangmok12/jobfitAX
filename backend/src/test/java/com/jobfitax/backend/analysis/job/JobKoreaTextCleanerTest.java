package com.jobfitax.backend.analysis.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jobfitax.backend.analysis.source.UrlExtractionResult;

class JobKoreaTextCleanerTest {

    private final JobKoreaTextCleaner cleaner = new JobKoreaTextCleaner();

    @Test
    void keepsJobAndCompanyFactsWhileRemovingApplicantStatsAndRecommendations() {
        String text = "쿠팡 지게차 채용 모집요강 경력무관 "
                + "이 기업과 나의 적합도 체크 핵심 역량 회사에서 중요하게 생각하는 역량과 가치가 나와 맞는지 알아보기 "
                + "회사 나 지금 로그인 하면 나와 회사의 적합도를 비교해볼 수 있어요. "
                + "지원자 현황 통계 지원자 수 5명 로그인/회원가입 "
                + "기업 정보 사원수 10,001명 이상 복리후생 건강검진 "
                + "관련 태그 지게차운전 추천공고 다른 회사 채용";

        UrlExtractionResult result = cleaner.clean(success(text));

        assertThat(result.normalizedText())
                .contains("쿠팡 지게차 채용", "경력무관", "기업 정보", "건강검진")
                .doesNotContain(
                        "이 기업과 나의 적합도 체크",
                        "나와 회사의 적합도를 비교",
                        "지원자 수",
                        "로그인/회원가입",
                        "관련 태그",
                        "다른 회사 채용"
                );
        assertThat(result.analysisCharacterCount()).isLessThan(text.length());
        assertThat(result.removalStats()).extracting("category")
                .contains("잡코리아 추천·고지·UI 영역");
    }

    private UrlExtractionResult success(String text) {
        return new UrlExtractionResult(
                "https://www.jobkorea.co.kr/Recruit/GI_Read/1",
                "https://www.jobkorea.co.kr/Recruit/GI_Read/1",
                "채용공고",
                "SUCCESS",
                text.length(),
                text.length(),
                text.length(),
                0,
                0,
                false,
                text,
                text,
                List.of(),
                null
        );
    }
}
