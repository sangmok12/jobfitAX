package com.jobfitax.backend.analysis.job;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jobfitax.backend.analysis.source.RemovalStat;
import com.jobfitax.backend.analysis.source.UrlExtractionResult;

@Component
public class SaraminTextCleaner {

    private static final List<String> CUT_MARKERS = List.of(
            "이어보는 Ai매치 채용정보",
            "이어보는 AI매치 채용정보",
            "이 공고와 함께 본 공고",
            "비슷한 공고",
            "추천 공고",
            "* 본 기업정보는",
            "사람인 고객센터",
            "Copyright ©"
    );

    private static final List<String> UI_PHRASES = List.of(
            "로그인 회원가입 메뉴 홈 채용정보 포지션 제안 신입·인턴 기업·연봉 커뮤니티 닫기",
            "스크랩",
            "관심기업 등록",
            "신고하기",
            "공유하기",
            "최저임금계산에 대한 알림",
            "지원자 통계",
            "입사지원 현황",
            "자격요건상세보기",
            "우대사항상세보기",
            "닫기 - 자격요건 상세",
            "닫기 - 우대사항 상세",
            "페이스북 트위터 URL복사 SMS발송",
            "지도 보기 지도 보기 지도 스카이뷰 지도초기화 크게보기 길찾기 닫기",
            "하단에 명시된 급여, 근무 내용 등이 최저임금에 미달하는 경우 위 내용이 우선합니다.",
            "마감일은 기업의 사정, 조기마감 등으로 변경될 수 있습니다."
    );

    public UrlExtractionResult clean(UrlExtractionResult source) {
        if (!"SUCCESS".equals(source.status())) {
            return source;
        }

        String original = source.normalizedText();
        String cleaned = cutAtFirst(original, CUT_MARKERS);
        for (String phrase : UI_PHRASES) {
            cleaned = cleaned.replace(phrase, " ");
        }
        cleaned = cleaned.replaceAll("조회수\\s*[0-9,]+", " ");
        cleaned = cleaned.replaceAll("홈페이지접속\\s*[0-9,]+", " ");
        cleaned = cleaned.replaceAll("남은 기간\\s*[0-9 ]*일\\s*[0-9: ]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").strip();

        int removedCharacters = Math.max(0, original.length() - cleaned.length());
        if (removedCharacters == 0) {
            return source;
        }

        List<RemovalStat> removalStats = new ArrayList<>(source.removalStats());
        removalStats.add(new RemovalStat("사람인 메뉴·추천·고지 영역", removedCharacters));

        return new UrlExtractionResult(
                source.sourceUrl(), source.finalUrl(), source.pageTitle(), source.status(),
                source.rawCharacterCount(), cleaned.length(), cleaned.length(),
                Math.max(0, source.rawCharacterCount() - cleaned.length()), 0, false,
                source.rawText(), cleaned, List.copyOf(removalStats), source.errorMessage()
        );
    }

    private String cutAtFirst(String text, List<String> markers) {
        int cutIndex = text.length();
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0) {
                cutIndex = Math.min(cutIndex, index);
            }
        }
        return text.substring(0, cutIndex);
    }
}
