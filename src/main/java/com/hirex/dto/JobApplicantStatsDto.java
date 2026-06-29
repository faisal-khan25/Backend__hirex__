package com.hirex.dto;



/**
 * Per-job applicant statistics returned by GET /api/jobs/{jobId}/stats.
 *
 * All counts are scoped to the single selected job so the ATS Analysis
 * page header always reflects the currently-viewed job rather than the
 * entire database.
 *
 * Stat definitions
 * ────────────────
 * totalApplicants      – total applications for this job (any status)
 * hired                – applications with status = HIRED
 * shortlisted          – applications with status = SHORTLISTED
 *                        (manual shortlist OR ATS shortlist promoted to status)
 * rejected             – applications with status = REJECTED
 * interviewAssigned    – applicants who have an InterviewSession (any status)
 * interviewCompleted   – applicants whose InterviewSession status = COMPLETED
 * atsShortlisted       – applications where atsStatus = 'SHORTLISTED'
 *                        (ATS-specific, may differ from manual shortlist count)
 * atsRejected          – applications where atsStatus = 'REJECTED'
 * pending              – applications still in APPLIED status (not yet processed)
 */
public class JobApplicantStatsDto {

    private Long   jobId;
    private String jobTitle;

    // ── Core applicant counts ────────────────────────────────────────────
    private long totalApplicants;
    private long hired;
    private long shortlisted;
    private long rejected;
    private long pending;

    // ── Interview pipeline ───────────────────────────────────────────────
    private long interviewAssigned;
    private long interviewCompleted;

    // ── ATS-specific (atsStatus field, set by ATS engine) ────────────────
    private long atsShortlisted;
    private long atsRejected;
    // ADD to builder and class:
    private long interviewPassed;
    private long interviewFailed;
    private long interviewUnderReview;

    public JobApplicantStatsDto() {}

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final JobApplicantStatsDto dto = new JobApplicantStatsDto();

        public Builder jobId(Long v)               { dto.jobId = v;               return this; }
        public Builder jobTitle(String v)          { dto.jobTitle = v;            return this; }
        public Builder totalApplicants(long v)     { dto.totalApplicants = v;     return this; }
        public Builder hired(long v)               { dto.hired = v;               return this; }
        public Builder shortlisted(long v)         { dto.shortlisted = v;         return this; }
        public Builder rejected(long v)            { dto.rejected = v;            return this; }
        public Builder pending(long v)             { dto.pending = v;             return this; }
        public Builder interviewAssigned(long v)   { dto.interviewAssigned = v;   return this; }
        public Builder interviewCompleted(long v)  { dto.interviewCompleted = v;  return this; }
        public Builder atsShortlisted(long v)      { dto.atsShortlisted = v;      return this; }
        public Builder atsRejected(long v)         { dto.atsRejected = v;         return this; }
        public Builder interviewPassed(long v)     { dto.interviewPassed = v;     return this; }
        public Builder interviewFailed(long v)     { dto.interviewFailed = v;     return this; }
        public Builder interviewUnderReview(long v){ dto.interviewUnderReview = v;return this; }


        public JobApplicantStatsDto build() { return dto; }
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public Long   getJobId()              { return jobId; }
    public String getJobTitle()           { return jobTitle; }
    public long   getTotalApplicants()    { return totalApplicants; }
    public long   getHired()              { return hired; }
    public long   getShortlisted()        { return shortlisted; }
    public long   getRejected()           { return rejected; }
    public long   getPending()            { return pending; }
    public long   getInterviewAssigned()  { return interviewAssigned; }
    public long   getInterviewCompleted() { return interviewCompleted; }
    public long   getAtsShortlisted()     { return atsShortlisted; }
    public long   getAtsRejected()        { return atsRejected; }
    public long getInterviewPassed() {
        return interviewPassed;
    }

    public long getInterviewFailed() {
        return interviewFailed;
    }

    public long getInterviewUnderReview() {
        return interviewUnderReview;
    }

    // ── Setters (for Jackson deserialization if needed) ───────────────────

    public void setJobId(Long v)              { this.jobId = v; }
    public void setJobTitle(String v)         { this.jobTitle = v; }
    public void setTotalApplicants(long v)    { this.totalApplicants = v; }
    public void setHired(long v)              { this.hired = v; }
    public void setShortlisted(long v)        { this.shortlisted = v; }
    public void setRejected(long v)           { this.rejected = v; }
    public void setPending(long v)            { this.pending = v; }
    public void setInterviewAssigned(long v)  { this.interviewAssigned = v; }
    public void setInterviewCompleted(long v) { this.interviewCompleted = v; }
    public void setAtsShortlisted(long v)     { this.atsShortlisted = v; }
    public void setAtsRejected(long v)        { this.atsRejected = v; }
    public void setInterviewPassed(long interviewPassed) {
        this.interviewPassed = interviewPassed;
    }

    public void setInterviewFailed(long interviewFailed) {
        this.interviewFailed = interviewFailed;
    }

    public void setInterviewUnderReview(long interviewUnderReview) {
        this.interviewUnderReview = interviewUnderReview;
    }
}
