package com.hirex.dto;

import java.util.List;

public class AtsResultDto {

    private int atsScore;
    private List<String> matchedKeywords;
    private List<String> missingKeywords;
    private List<String> suggestions;

    // Auto-shortlist fields (populated only from /ats-autocheck endpoint)
    private boolean autoShortlisted;
    private boolean alreadyShortlisted;
    private String shortlistMessage;
    private Long   applicationId;
    private String candidateName;
    private String jobTitle;

    public AtsResultDto() {}

    public AtsResultDto(int atsScore,
                        List<String> matchedKeywords,
                        List<String> missingKeywords,
                        List<String> suggestions) {
        this.atsScore        = atsScore;
        this.matchedKeywords = matchedKeywords;
        this.missingKeywords = missingKeywords;
        this.suggestions     = suggestions;
    }

    public int getAtsScore() { return atsScore; }
    public void setAtsScore(int atsScore) { this.atsScore = atsScore; }

    public List<String> getMatchedKeywords() { return matchedKeywords; }
    public void setMatchedKeywords(List<String> matchedKeywords) { this.matchedKeywords = matchedKeywords; }

    public List<String> getMissingKeywords() { return missingKeywords; }
    public void setMissingKeywords(List<String> missingKeywords) { this.missingKeywords = missingKeywords; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public boolean isAutoShortlisted() { return autoShortlisted; }
    public void setAutoShortlisted(boolean autoShortlisted) { this.autoShortlisted = autoShortlisted; }

    public boolean isAlreadyShortlisted() { return alreadyShortlisted; }
    public void setAlreadyShortlisted(boolean alreadyShortlisted) { this.alreadyShortlisted = alreadyShortlisted; }

    public String getShortlistMessage() { return shortlistMessage; }
    public void setShortlistMessage(String shortlistMessage) { this.shortlistMessage = shortlistMessage; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
}