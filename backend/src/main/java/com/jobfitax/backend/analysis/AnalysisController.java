package com.jobfitax.backend.analysis;

import java.net.URI;
import java.util.Locale;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.jobfitax.backend.analysis.job.JobPostingExtractionResult;
import com.jobfitax.backend.analysis.job.JobPostingTextExtractor;
import com.jobfitax.backend.analysis.source.DocumentTextExtractor;
import com.jobfitax.backend.analysis.source.SourceExtractionResult;
import com.jobfitax.backend.analysis.source.UrlExtractionResult;
import com.jobfitax.backend.analysis.source.WebPageTextExtractor;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private static final int MAX_SOURCE_COUNT = 10;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown",
            "application/octet-stream"
    );

    private final DocumentTextExtractor documentTextExtractor;
    private final WebPageTextExtractor webPageTextExtractor;
    private final JobPostingTextExtractor jobPostingTextExtractor;
    private final JobFitAnalysisService jobFitAnalysisService;

    public AnalysisController(
            DocumentTextExtractor documentTextExtractor,
            WebPageTextExtractor webPageTextExtractor,
            JobPostingTextExtractor jobPostingTextExtractor,
            JobFitAnalysisService jobFitAnalysisService
    ) {
        this.documentTextExtractor = documentTextExtractor;
        this.webPageTextExtractor = webPageTextExtractor;
        this.jobPostingTextExtractor = jobPostingTextExtractor;
        this.jobFitAnalysisService = jobFitAnalysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisRequestResponse receiveAnalysisRequest(
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) List<String> sourceUrls,
            @RequestParam(defaultValue = "") String preferences,
            @RequestParam String jobPostingUrl
    ) {
        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        List<String> safeSourceUrls = sourceUrls == null ? List.of() : sourceUrls;

        validateJobPostingUrl(jobPostingUrl);
        validateUserInput(safeFiles, safeSourceUrls, preferences);
        validateSourceCount(safeFiles, safeSourceUrls);
        safeFiles.forEach(this::validateFile);
        safeSourceUrls.forEach(this::validateSourceUrl);

        List<String> receivedFiles = safeFiles.stream()
                .filter(file -> !file.isEmpty())
                .map(MultipartFile::getOriginalFilename)
                .filter(StringUtils::hasText)
                .map(StringUtils::cleanPath)
                .toList();

        List<SourceExtractionResult> extractedSources = safeFiles.stream()
                .map(documentTextExtractor::extract)
                .toList();
        List<UrlExtractionResult> extractedUrls = safeSourceUrls.stream()
                .map(webPageTextExtractor::extract)
                .toList();
        JobPostingExtractionResult jobPosting = jobPostingTextExtractor.extract(jobPostingUrl);

        return new AnalysisRequestResponse(
                "RECEIVED", jobPostingUrl, jobPosting, preferences, receivedFiles, safeSourceUrls,
                extractedSources, extractedUrls
        );
    }

    private void validateUserInput(List<MultipartFile> files, List<String> sourceUrls, String preferences) {
        boolean hasFile = files.stream().anyMatch(file -> !file.isEmpty());
        boolean hasUrl = sourceUrls.stream().anyMatch(StringUtils::hasText);
        if (!hasFile && !hasUrl && !StringUtils.hasText(preferences)) {
            throw badRequest("적합도를 분석하려면 파일, 공개 URL 또는 직접 작성한 사용자 정보를 하나 이상 추가해 주세요.");
        }
    }

    @PostMapping(path = "/job-fit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JobFitAnalysisResult analyzeJobFit(@RequestBody JobFitAnalysisRequest request) {
        return jobFitAnalysisService.analyze(request);
    }

    private void validateSourceCount(List<MultipartFile> files, List<String> sourceUrls) {
        if (files.size() > MAX_SOURCE_COUNT || sourceUrls.size() > MAX_SOURCE_COUNT) {
            throw badRequest("파일과 URL은 각각 최대 10개까지 추가할 수 있습니다.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw badRequest("비어 있는 파일은 첨부할 수 없습니다.");
        }

        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(filename);
        String contentType = file.getContentType();

        if (!StringUtils.hasText(extension)
                || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))
                || !StringUtils.hasText(contentType)
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw badRequest("지원하지 않는 파일 형식입니다. PDF, DOCX, TXT, MD 파일만 첨부해 주세요.");
        }
    }

    private void validateSourceUrl(String sourceUrl) {
        validateHttpUrl(sourceUrl, "첨부 자료 URL 형식이 올바르지 않습니다.");
    }

    private void validateJobPostingUrl(String jobPostingUrl) {
        validateHttpUrl(jobPostingUrl, "채용공고 URL 형식이 올바르지 않습니다.");
    }

    private void validateHttpUrl(String url, String errorMessage) {
        try {
            URI uri = URI.create(url);
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());

            if (!supportedScheme || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest(errorMessage);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
