package com.jobfitax.backend.analysis;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisRequestResponse receiveAnalysisRequest(
            @RequestParam(required = false) MultipartFile resume,
            @RequestParam(required = false) MultipartFile career,
            @RequestParam(required = false) MultipartFile portfolio,
            @RequestParam(defaultValue = "") String preferences,
            @RequestParam String jobPostingUrl
    ) {
        validateJobPostingUrl(jobPostingUrl);

        List<String> receivedFiles = Stream.of(resume, career, portfolio)
                .filter(Objects::nonNull)
                .filter(file -> !file.isEmpty())
                .map(MultipartFile::getOriginalFilename)
                .filter(Objects::nonNull)
                .map(StringUtils::cleanPath)
                .toList();

        return new AnalysisRequestResponse("RECEIVED", jobPostingUrl, receivedFiles);
    }

    private void validateJobPostingUrl(String jobPostingUrl) {
        try {
            URI uri = URI.create(jobPostingUrl);
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());

            if (!supportedScheme || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "채용공고 URL 형식이 올바르지 않습니다."
            );
        }
    }
}
