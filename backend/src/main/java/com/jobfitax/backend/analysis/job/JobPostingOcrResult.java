package com.jobfitax.backend.analysis.job;

public record JobPostingOcrResult(
        String status,
        int detectedImageCount,
        int includedImageCount,
        int characterCount,
        String text,
        String message
) {
    public static JobPostingOcrResult noImage() {
        return new JobPostingOcrResult("NO_IMAGE", 0, 0, 0, "", "상세공고 이미지를 찾지 못했습니다.");
    }
}
