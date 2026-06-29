package com.hirex.dto;

import java.util.List;

/**
 * Response DTO for job-specific ATS operations.
 */
public class JobAtsResponseDto {

    private Long   jobId;
    private String jobTitle;
    private int    threshold;
    private int    totalProcessed;
    private int    shortlisted;
    private int    rejected;
    private int    skipped;
    private String message;
    private List<CandidateAtsResult> candidates;

    // ── Static factories ─────────────────────────────────────────────────

    public static JobAtsResponseDto empty(String jobTitle, Long jobId) {
        return builder()
                .jobId(jobId)
                .jobTitle(jobTitle)
                .threshold(0)
                .totalProcessed(0)
                .shortlisted(0)
                .rejected(0)
                .skipped(0)
                .candidates(List.of())
                .message("No applicants found for job: " + jobTitle)
                .build();
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final JobAtsResponseDto dto = new JobAtsResponseDto();
        public Builder jobId(Long v)          { dto.jobId = v; return this; }
        public Builder jobTitle(String v)     { dto.jobTitle = v; return this; }
        public Builder threshold(int v)       { dto.threshold = v; return this; }
        public Builder totalProcessed(int v)  { dto.totalProcessed = v; return this; }
        public Builder shortlisted(int v)     { dto.shortlisted = v; return this; }
        public Builder rejected(int v)        { dto.rejected = v; return this; }
        public Builder skipped(int v)         { dto.skipped = v; return this; }
        public Builder message(String v)      { dto.message = v; return this; }
        public Builder candidates(List<CandidateAtsResult> v) { dto.candidates = v; return this; }
        public JobAtsResponseDto build()      { return dto; }
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────

    public static class CandidateAtsResult {
        private Long         applicationId;
        private String       candidateName;
        private String       candidateEmail;
        private int          atsScore;
        private String       atsStatus;
        private List<String> matchedSkills;
        private List<String> missingSkills;
        private String       note;

        public static CandidateBuilder builder() { return new CandidateBuilder(); }

        public static class CandidateBuilder {
            private final CandidateAtsResult r = new CandidateAtsResult();
            public CandidateBuilder applicationId(Long v)       { r.applicationId = v; return this; }
            public CandidateBuilder candidateName(String v)     { r.candidateName = v; return this; }
            public CandidateBuilder candidateEmail(String v)    { r.candidateEmail = v; return this; }
            public CandidateBuilder atsScore(int v)             { r.atsScore = v; return this; }
            public CandidateBuilder atsStatus(String v)         { r.atsStatus = v; return this; }
            public CandidateBuilder matchedSkills(List<String> v){ r.matchedSkills = v; return this; }
            public CandidateBuilder missingSkills(List<String> v){ r.missingSkills = v; return this; }
            public CandidateBuilder note(String v)              { r.note = v; return this; }
            public CandidateAtsResult build()                   { return r; }
        }

        // Getters
        public Long         getApplicationId()  { return applicationId; }
        public String       getCandidateName()  { return candidateName; }
        public String       getCandidateEmail() { return candidateEmail; }
        public int          getAtsScore()       { return atsScore; }
        public String       getAtsStatus()      { return atsStatus; }
        public List<String> getMatchedSkills()  { return matchedSkills; }
        public List<String> getMissingSkills()  { return missingSkills; }
        public String       getNote()           { return note; }
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public Long   getJobId()         { return jobId; }
    public String getJobTitle()      { return jobTitle; }
    public int    getThreshold()     { return threshold; }
    public int    getTotalProcessed(){ return totalProcessed; }
    public int    getShortlisted()   { return shortlisted; }
    public int    getRejected()      { return rejected; }
    public int    getSkipped()       { return skipped; }
    public String getMessage()       { return message; }
    public List<CandidateAtsResult> getCandidates() { return candidates; }
}
