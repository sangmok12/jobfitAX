package com.jobfitax.backend.analysis.source;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TextNormalizer {

    public NormalizationResult normalize(String rawText) {
        List<String> rules = new ArrayList<>();
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n');

        String unicodeNormalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        if (!unicodeNormalized.equals(text)) {
            rules.add("유니코드 문자 표준화");
            text = unicodeNormalized;
        }

        String withoutControls = text.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
        if (!withoutControls.equals(text)) {
            rules.add("제어문자 제거");
            text = withoutControls;
        }

        String compactLines = text.lines()
                .map(line -> line.strip().replaceAll("[ \\t]+", " "))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (!compactLines.equals(text)) {
            rules.add("줄 끝과 연속 공백 정리");
            text = compactLines;
        }

        String compactBlankLines = text.replaceAll("\\n{3,}", "\n\n").strip();
        if (!compactBlankLines.equals(text)) {
            rules.add("과도한 빈 줄 정리");
            text = compactBlankLines;
        }

        return new NormalizationResult(
                text,
                Math.max(0, rawText.length() - text.length()),
                List.copyOf(rules)
        );
    }
}
