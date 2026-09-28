package com.jobfitax.backend.analysis;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.jobfitax.backend.analysis.JobFitAnalysisResult.Assessment;
import com.jobfitax.backend.analysis.JobFitAnalysisResult.Usage;
import com.jobfitax.backend.analysis.OpenAiJobFitClient.AiAnalysisException;
import com.jobfitax.backend.analysis.OpenAiJobFitClient.AiResponse;

@Service
public class JobFitAnalysisService {

    private static final int MAX_AI_INPUT_CHARACTERS = 180_000;
    private static final String SYSTEM_PROMPT = """
            당신은 채용 적합도를 분석하는 JobFit AX의 분석가입니다.
            사용자 자료와 채용공고를 한국어로 의미적으로 비교하세요.

            판단 원칙:
            1. 단순 키워드 일치가 아니라 실제 업무, 기술, 책임, 성과의 의미를 비교합니다.
            2. 채용공고의 필수조건과 우대조건을 구분합니다.
            3. 제공된 자료에 없는 경력, 기술, 회사 정보는 절대 추측하지 않습니다.
            4. 정보가 없으면 낮은 점수나 0점을 주지 말고 status를 INSUFFICIENT_INFORMATION, score를 null로 반환합니다.
            5. 평가한 항목은 status를 EVALUATED로 하고 0~100 사이 정수 점수와 구체적인 근거를 제공합니다.
            6. OCR 오타가 있더라도 주변 문맥상 명확한 경우 문맥을 기준으로 해석하되, 불확실하면 한계에 기록합니다.
            7. 강점과 부족한 점에는 사용자 근거와 채용공고 근거를 함께 제공합니다. 근거가 없으면 빈 문자열을 사용합니다.
            8. 연봉, 직원 수, 복지, 문화, 기술스택 등 확인되지 않은 정보는 unknownInformation에 기록합니다.
            9. 설명은 친절하고 구체적으로 작성하되 과장하거나 합격 가능성을 단정하지 않습니다.

            평가 항목:
            - skill: 사용자가 확인 가능한 기술과 공고에서 요구하는 기술의 적합성
            - experience: 경력 연차, 책임 수준, 프로젝트 성과와 공고 요구 경력의 적합성
            - role: 사용자의 실제 업무 경험과 채용 포지션 담당 업무의 적합성
            - preference: 사용자가 밝힌 희망 직무, 근무지역, 근무형태, 관심 분야 등과 공고 조건의 적합성
            """;

    private final OpenAiJobFitClient openAiClient;

    public JobFitAnalysisService(OpenAiJobFitClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public JobFitAnalysisResult analyze(JobFitAnalysisRequest request) {
        validate(request);
        String prompt = buildPrompt(request);

        try {
            AiResponse aiResponse = openAiClient.analyze(SYSTEM_PROMPT, prompt);
            AiResult aiResult = openAiClient.parse(aiResponse.outputText(), AiResult.class);
            Integer overallScore = calculateOverall(aiResult);
            String overallStatus = overallScore == null ? "INSUFFICIENT_INFORMATION" : "EVALUATED";

            return new JobFitAnalysisResult(
                    safeText(aiResult.companyName(), "회사명 확인 불가"),
                    safeText(aiResult.position(), "채용 포지션 확인 불가"),
                    overallScore,
                    overallStatus,
                    aiResult.summary(),
                    aiResult.skill(),
                    aiResult.experience(),
                    aiResult.role(),
                    aiResult.preference(),
                    safeList(aiResult.strengths()),
                    safeList(aiResult.gaps()),
                    safeList(aiResult.applicationTips()),
                    safeList(aiResult.preparations()),
                    aiResult.jobInformation(),
                    safeList(aiResult.dataLimitations()),
                    new Usage(aiResponse.model(), aiResponse.inputTokens(), aiResponse.outputTokens(),
                            aiResponse.totalTokens(), aiResponse.estimatedCostUsd())
            );
        } catch (AiAnalysisException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    private void validate(JobFitAnalysisRequest request) {
        if (request == null || !StringUtils.hasText(request.jobPostingText())) {
            throw badRequest("분석할 채용공고 내용이 없습니다. 채용공고를 다시 정리해 주세요.");
        }

        boolean hasPreferences = StringUtils.hasText(request.preferences());
        boolean hasMaterial = request.userMaterials() != null && request.userMaterials().stream()
                .anyMatch(material -> material != null && StringUtils.hasText(material.text()));
        if (!hasPreferences && !hasMaterial) {
            throw badRequest("적합도 분석을 위해 파일, 공개 URL 또는 직접 작성한 사용자 정보를 하나 이상 입력해 주세요.");
        }
    }

    private String buildPrompt(JobFitAnalysisRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음 자료를 기준으로 JobFit 적합도를 분석하고 지정된 JSON 형식으로 응답하세요.\n\n");
        prompt.append("[사용자 직접 작성 정보 및 희망사항]\n")
                .append(StringUtils.hasText(request.preferences()) ? request.preferences().trim() : "제공되지 않음")
                .append("\n\n[사용자 첨부 자료]\n");

        List<JobFitAnalysisRequest.UserMaterial> materials = request.userMaterials() == null
                ? List.of() : request.userMaterials();
        int materialIndex = 1;
        for (JobFitAnalysisRequest.UserMaterial material : materials) {
            if (material == null || !StringUtils.hasText(material.text())) continue;
            prompt.append("--- 자료 ").append(materialIndex++).append(": ")
                    .append(safeText(material.name(), "이름 없는 자료")).append(" ---\n")
                    .append(material.text().trim()).append("\n\n");
        }
        if (materialIndex == 1) prompt.append("제공되지 않음\n\n");

        prompt.append("[채용공고 출처]\n")
                .append(safeText(request.jobPostingSite(), "출처 확인 불가")).append("\n")
                .append(safeText(request.jobPostingUrl(), "URL 확인 불가")).append("\n\n")
                .append("[채용공고 본문]\n")
                .append(request.jobPostingText().trim());

        if (prompt.length() > MAX_AI_INPUT_CHARACTERS) {
            throw badRequest("AI에 전달할 내용이 너무 깁니다. 첨부 자료 수나 내용 길이를 줄여 주세요.");
        }
        return prompt.toString();
    }

    private Integer calculateOverall(AiResult result) {
        if (!evaluated(result.skill()) || !evaluated(result.experience()) || !evaluated(result.role())) {
            return null;
        }

        double weightedTotal = result.skill().score() * 0.30
                + result.experience().score() * 0.30
                + result.role().score() * 0.25;
        double weight = 0.85;
        if (evaluated(result.preference())) {
            weightedTotal += result.preference().score() * 0.15;
            weight += 0.15;
        }
        return (int) Math.round(weightedTotal / weight);
    }

    private boolean evaluated(Assessment assessment) {
        return assessment != null
                && "EVALUATED".equals(assessment.status())
                && assessment.score() != null;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private record AiResult(
            String companyName,
            String position,
            String summary,
            Assessment skill,
            Assessment experience,
            Assessment role,
            Assessment preference,
            List<JobFitAnalysisResult.Insight> strengths,
            List<JobFitAnalysisResult.Insight> gaps,
            List<String> applicationTips,
            List<String> preparations,
            JobFitAnalysisResult.JobInformation jobInformation,
            List<String> dataLimitations
    ) {
    }
}
