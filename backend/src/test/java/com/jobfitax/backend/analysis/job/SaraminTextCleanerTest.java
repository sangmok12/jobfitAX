package com.jobfitax.backend.analysis.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jobfitax.backend.analysis.source.UrlExtractionResult;

class SaraminTextCleanerTest {

    private final SaraminTextCleaner cleaner = new SaraminTextCleaner();

    @Test
    void removesSaraminControlsAndKeepsJobConditions() {
        String text = "회사명 백엔드 개발자 핵심 정보 경력 3년 자격요건상세보기 "
                + "Java Spring 닫기 - 자격요건 상세 조회수 1,234 "
                + "하단에 명시된 급여, 근무 내용 등이 최저임금에 미달하는 경우 위 내용이 우선합니다.";

        UrlExtractionResult result = cleaner.clean(success(text));

        assertThat(result.normalizedText())
                .contains("회사명 백엔드 개발자", "경력 3년", "Java Spring")
                .doesNotContain("상세보기", "조회수", "최저임금");
    }

    @Test
    void cutsCompanyDisclaimerAndFollowingNoise() {
        String text = "기업형태 중소기업 업종 소프트웨어 개발 * 본 기업정보는 제공된 자료입니다 추천 공고";

        UrlExtractionResult result = cleaner.clean(success(text));

        assertThat(result.normalizedText()).isEqualTo("기업형태 중소기업 업종 소프트웨어 개발");
    }

    private UrlExtractionResult success(String text) {
        return new UrlExtractionResult(
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1",
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1",
                "공고", "SUCCESS", text.length(), text.length(), text.length(),
                0, 0, false, text, text, List.of(), null
        );
    }
}
