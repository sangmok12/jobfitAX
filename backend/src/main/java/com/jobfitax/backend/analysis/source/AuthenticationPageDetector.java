package com.jobfitax.backend.analysis.source;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class AuthenticationPageDetector {

    public boolean isAuthenticationPage(String text) {
        String normalizedText = text.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("\\s+", " ");

        boolean notionLogin = normalizedText.contains("you're almost there")
                && normalizedText.contains("sign in to see this page");
        boolean socialLoginWall = normalizedText.contains("sign in to see this page")
                && (normalizedText.contains("continue with google")
                        || normalizedText.contains("continue with apple")
                        || normalizedText.contains("continue with microsoft"));
        boolean koreanLoginWall = normalizedText.contains("이 페이지를 보려면 로그인")
                || normalizedText.contains("로그인 후 이용할 수 있습니다");

        return notionLogin || socialLoginWall || koreanLoginWall;
    }
}
