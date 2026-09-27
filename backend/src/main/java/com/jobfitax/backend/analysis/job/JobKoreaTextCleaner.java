package com.jobfitax.backend.analysis.job;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jobfitax.backend.analysis.source.RemovalStat;
import com.jobfitax.backend.analysis.source.UrlExtractionResult;

@Component
public class JobKoreaTextCleaner {

    private static final List<String> UI_PHRASES = List.of(
            "상세요강 접수기간∙방법 기업정보 추천공고",
            "채용정보에 잘못된 내용이 있을 경우 문의 해주세요.",
            "신입·인턴 채용관",
            "전역 장병 우대채용관",
            "잡아바 추천 TOP 기업 온라인 채용관",
            "지도보기",
            "기업정보 더보기",
            "복리후생 더보기",
            "잡코리아 즉시지원",
            "지원양식 잡코리아 이력서",
            "마감일은 기업의 사정으로 인해 조기 마감 또는 변경될 수 있습니다"
    );

    public UrlExtractionResult clean(UrlExtractionResult source) {
        if (!"SUCCESS".equals(source.status())) {
            return source;
        }

        String original = source.normalizedText();
        String cleaned = cutAt(original, "관련 태그");
        cleaned = removeRange(cleaned, "지원자 현황 통계", "기업 정보");
        cleaned = removeFitCheckPrompt(cleaned);
        for (String phrase : UI_PHRASES) {
            cleaned = cleaned.replace(phrase, " ");
        }
        cleaned = cleaned.replaceAll("\\s+", " ").strip();

        int removedCharacters = Math.max(0, original.length() - cleaned.length());
        if (removedCharacters == 0) {
            return source;
        }

        List<RemovalStat> removalStats = new ArrayList<>(source.removalStats());
        removalStats.add(new RemovalStat("잡코리아 추천·고지·UI 영역", removedCharacters));

        return new UrlExtractionResult(
                source.sourceUrl(),
                source.finalUrl(),
                source.pageTitle(),
                source.status(),
                source.rawCharacterCount(),
                cleaned.length(),
                cleaned.length(),
                Math.max(0, source.rawCharacterCount() - cleaned.length()),
                0,
                false,
                source.rawText(),
                cleaned,
                List.copyOf(removalStats),
                source.errorMessage()
        );
    }

    private String cutAt(String text, String marker) {
        int markerIndex = text.indexOf(marker);
        return markerIndex >= 0 ? text.substring(0, markerIndex) : text;
    }

    private String removeRange(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return text;
        }
        int end = text.indexOf(endMarker, start + startMarker.length());
        if (end < 0) {
            return text;
        }
        return text.substring(0, start) + " " + text.substring(end);
    }

    private String removeFitCheckPrompt(String text) {
        return text.replaceAll(
                "이 기업과 나의 적합도 체크.*?로그인\\s*하면 나와 회사의 적합도를 비교해볼 수 있어요\\.?",
                " "
        );
    }
}
