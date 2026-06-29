package com.hirex.dto;

import java.util.List;

/**
 * Response envelope for POST /api/ats/analyze-all and POST /api/ats/process-all
 */
public class AtsBulkResponseDto {

    private int totalProcessed;
    private int totalSkipped;       // no resume text
    private int totalHired;
    private int totalShortlisted;
    private int totalRejected;
    private String message;

    private List<AtsBulkResultDto> results;

    public AtsBulkResponseDto() {}

    /* ───────── getters / setters ───────── */

    public int getTotalProcessed() { return totalProcessed; }
    public void setTotalProcessed(int totalProcessed) { this.totalProcessed = totalProcessed; }

    public int getTotalSkipped() { return totalSkipped; }
    public void setTotalSkipped(int totalSkipped) { this.totalSkipped = totalSkipped; }

    public int getTotalHired() { return totalHired; }
    public void setTotalHired(int totalHired) { this.totalHired = totalHired; }

    public int getTotalShortlisted() { return totalShortlisted; }
    public void setTotalShortlisted(int totalShortlisted) { this.totalShortlisted = totalShortlisted; }

    public int getTotalRejected() { return totalRejected; }
    public void setTotalRejected(int totalRejected) { this.totalRejected = totalRejected; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<AtsBulkResultDto> getResults() { return results; }
    public void setResults(List<AtsBulkResultDto> results) { this.results = results; }
}
