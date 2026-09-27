package com.jobfitax.backend.analysis.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jobfitax.backend.analysis.source.RenderedWebPageFetcher.RenderedFrame;
import com.jobfitax.backend.analysis.source.TextNormalizer;

class JobKoreaImageOcrServiceTest {

    private final JobKoreaImageOcrService service = new JobKoreaImageOcrService(null, null, null);

    @Test
    void removesDecorativeNoiseAndLegalNoticeButKeepsJobFacts() {
        String rawOcr = """
                coupang logistics services
                차차 Io =
                근무형태 및 지원자격
                FO AAA 및 전동자키를 이용한 상하차 업무
                입출고 과정에서 수반되는 상품 분류 업무
                지원 운전테스트/건강검진 최종 결과 통보 입사
                유의사항
                채용 서류에 허위 사실이 발견될 경우 취소될 수 있습니다.
                """;

        String cleaned = service.cleanOcrText(rawOcr);

        assertThat(cleaned)
                .contains("근무형태 및 지원자격", "상하차 업무", "상품 분류 업무", "건강검진")
                .doesNotContain("차차 Io", "유의사항", "허위 사실");
    }

    @Test
    void usesDetailedFrameTextWithoutRunningOcrWhenJobFactsAreAlreadyReadable() {
        JobKoreaImageOcrService frameService = new JobKoreaImageOcrService(
                null, null, new TextNormalizer()
        );
        String html = """
                <html><body>
                <h1>배송기사 채용</h1>
                <p>담당업무: 식자재 배송 및 화물차량 운행</p>
                <p>지원자격: 경력무관, 화물운송 자격증 소지자</p>
                <p>근무시간: 주 6일, 노선별 근무시간 상이</p>
                <p>급여: 기본급 및 추가수당 지급</p>
                <p>교육: 운전연수와 선탑교육 진행</p>
                </body></html>
                """;

        JobPostingOcrResult result = frameService.extract(List.of(
                new RenderedFrame(URI.create("https://www.jobkorea.co.kr/detail"), html)
        ));

        assertThat(result.status()).isEqualTo("FRAME_TEXT");
        assertThat(result.text()).contains("담당업무", "지원자격", "근무시간", "급여");
        assertThat(result.includedImageCount()).isZero();
    }
}
