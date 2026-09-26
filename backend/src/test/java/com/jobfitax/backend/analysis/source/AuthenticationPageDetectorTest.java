package com.jobfitax.backend.analysis.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticationPageDetectorTest {

    private final AuthenticationPageDetector detector = new AuthenticationPageDetector();

    @Test
    void detectsNotionAuthenticationPage() {
        String text = """
                You’re almost there! Sign in to see this page in 상목 임의 Notion
                or continue with Google ChatGPT Apple Microsoft Passkey SSO
                New user? Sign up
                """;

        assertThat(detector.isAuthenticationPage(text)).isTrue();
    }

    @Test
    void doesNotBlockPortfolioThatMerelyMentionsLoginDevelopment() {
        String text = "React로 Google 로그인 기능을 구현했고 Spring Boot API와 연동했습니다.";

        assertThat(detector.isAuthenticationPage(text)).isFalse();
    }
}
