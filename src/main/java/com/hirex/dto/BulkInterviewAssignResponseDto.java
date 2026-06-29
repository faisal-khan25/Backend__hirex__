package com.hirex.dto;

public class BulkInterviewAssignResponseDto {

    private boolean success;
    private Long    jobId;
    private String  jobTitle;
    private int     assigned;
    private int     skipped;
    private int     alreadyAssigned;
    private String  message;

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final BulkInterviewAssignResponseDto dto = new BulkInterviewAssignResponseDto();
        public Builder success(boolean v)         { dto.success = v; return this; }
        public Builder jobId(Long v)              { dto.jobId = v; return this; }
        public Builder jobTitle(String v)         { dto.jobTitle = v; return this; }
        public Builder assigned(int v)            { dto.assigned = v; return this; }
        public Builder skipped(int v)             { dto.skipped = v; return this; }
        public Builder alreadyAssigned(int v)     { dto.alreadyAssigned = v; return this; }
        public Builder message(String v)          { dto.message = v; return this; }
        public BulkInterviewAssignResponseDto build() { return dto; }
    }

    // Getters
    public boolean isSuccess()        { return success; }
    public Long    getJobId()         { return jobId; }
    public String  getJobTitle()      { return jobTitle; }
    public int     getAssigned()      { return assigned; }
    public int     getSkipped()       { return skipped; }
    public int     getAlreadyAssigned(){ return alreadyAssigned; }
    public String  getMessage()       { return message; }
}
