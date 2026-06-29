package com.hirex.dto;

public class AtsSummaryDto {

    private long totalApplicants;
    private long totalHired;
    private long totalShortlisted;
    private long totalRejected;
    private long totalPending;      // APPLIED status – not yet processed
    private long totalWithResume;   // candidates who have uploaded a resume

    public AtsSummaryDto() {}

    public AtsSummaryDto(long totalApplicants, long totalHired,
                         long totalShortlisted, long totalRejected,
                         long totalPending, long totalWithResume) {
        this.totalApplicants  = totalApplicants;
        this.totalHired       = totalHired;
        this.totalShortlisted = totalShortlisted;
        this.totalRejected    = totalRejected;
        this.totalPending     = totalPending;
        this.totalWithResume  = totalWithResume;
    }

    /* ───────── getters / setters ───────── */

    public long getTotalApplicants() { return totalApplicants; }
    public void setTotalApplicants(long totalApplicants) { this.totalApplicants = totalApplicants; }

    public long getTotalHired() { return totalHired; }
    public void setTotalHired(long totalHired) { this.totalHired = totalHired; }

    public long getTotalShortlisted() { return totalShortlisted; }
    public void setTotalShortlisted(long totalShortlisted) { this.totalShortlisted = totalShortlisted; }

    public long getTotalRejected() { return totalRejected; }
    public void setTotalRejected(long totalRejected) { this.totalRejected = totalRejected; }

    public long getTotalPending() { return totalPending; }
    public void setTotalPending(long totalPending) { this.totalPending = totalPending; }

    public long getTotalWithResume() { return totalWithResume; }
    public void setTotalWithResume(long totalWithResume) { this.totalWithResume = totalWithResume; }
}
