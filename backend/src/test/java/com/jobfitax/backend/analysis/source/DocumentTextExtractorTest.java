package com.jobfitax.backend.analysis.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor(new TextNormalizer());

    @Test
    void reportsExpiredJobKoreaSessionPageSavedWithPdfExtension() {
        String html = "<html><body><script>alert('세션이 만료 되었습니다.');"
                + "location.href='/Login/Login_ToT.asp';</script></body></html>";
        MockMultipartFile file = new MockMultipartFile(
                "files", "잡코리아_이력서.pdf", "application/pdf", html.getBytes(StandardCharsets.UTF_8)
        );

        SourceExtractionResult result = extractor.extract(file);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage())
                .contains("로그인 세션 만료 페이지")
                .contains("다시 로그인")
                .contains("PDF로 다시 내려받아");
    }

    @Test
    void reportsGenericHtmlSavedWithPdfExtension() {
        String html = "<!doctype html><html><body>다운로드 오류</body></html>";
        MockMultipartFile file = new MockMultipartFile(
                "files", "이력서.pdf", "application/pdf", html.getBytes(StandardCharsets.UTF_8)
        );

        SourceExtractionResult result = extractor.extract(file);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("웹페이지 HTML");
    }
}
