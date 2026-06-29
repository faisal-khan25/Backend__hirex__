package com.hirex.dto;

import java.time.LocalDateTime;

public class ResumeDto {

    private Long resumeId;
    private Long userId;
    private String candidateName;
    private String candidateEmail;
    private String fileName;
    private String filePath;
    private LocalDateTime uploadedAt;
    private boolean hasText;

    /**
     * The application ID linking this resume/candidate to a specific job application.
     * Populated when there is exactly one application by this user.
     * Used by the ATS checker to auto-update application status after scoring.
     */
    private Long applicationId;

    public ResumeDto() {}

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getCandidateEmail() { return candidateEmail; }
    public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public boolean isHasText() { return hasText; }
    public void setHasText(boolean hasText) { this.hasText = hasText; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
}
