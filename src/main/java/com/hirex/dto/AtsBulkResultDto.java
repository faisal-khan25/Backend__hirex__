package com.hirex.dto;

public class AtsBulkResultDto {

    private Long   resumeId;
    private Long   userId;
    private Long   applicationId;   // nullable – if candidate applied to a specific job
    private String candidateName;
    private String candidateEmail;
    private String fileName;

    private int    atsScore;
    private double matchPercentage;  // same as atsScore but as a decimal for display
    private String status;           // HIRED | SHORTLISTED | REJECTED
    private boolean processed;       // false if resume had no text

    public AtsBulkResultDto() {}

    /* ───────── getters / setters ───────── */

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getCandidateEmail() { return candidateEmail; }
    public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getAtsScore() { return atsScore; }
    public void setAtsScore(int atsScore) { this.atsScore = atsScore; }

    public double getMatchPercentage() { return matchPercentage; }
    public void setMatchPercentage(double matchPercentage) { this.matchPercentage = matchPercentage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
}
