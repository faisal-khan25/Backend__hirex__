package com.hirex.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "interview_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_interview_application", columnNames = {"application_id"})
        },
        indexes = {
                @Index(name = "idx_interview_application", columnList = "application_id"),
                @Index(name = "idx_interview_status", columnList = "status"),
                @Index(name = "idx_interview_created", columnList = "created_at")
        }
)
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    // ADDED: Interview Type
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewType interviewType;

    // ADDED: Interview Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status;

    // ADDED: Timestamps
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // ADDED: Interview Link (unique access link for the candidate)
    @Column(name = "interview_link", unique = true, length = 512)
    private String interviewLink;

    // ADDED: Recording
    @Column(name = "recording_url")
    private String recordingUrl;

    // ADDED: Interview Configuration
    @Column(name = "interview_template")
    private String interviewTemplate;

    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;

    // ADDED: Interview Context
    @Column(columnDefinition = "LONGTEXT")
    private String interviewContext;

    @Column(columnDefinition = "LONGTEXT")
    private String jobDescription;

    @Column(name = "candidate_name")
    private String candidateName;

    @Column(name = "position_title")
    private String positionTitle;

    // ADDED: Metadata
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = InterviewStatus.PENDING;
        }
        if (maxDurationMinutes == null) {
            maxDurationMinutes = 60;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ADDED: Constructors
    public InterviewSession() {}

    public InterviewSession(Application application, InterviewType interviewType, String interviewTemplate) {
        this.application = application;
        this.interviewType = interviewType;
        this.interviewTemplate = interviewTemplate;
        this.status = InterviewStatus.PENDING;
    }

    // ADDED: Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public InterviewType getInterviewType() { return interviewType; }
    public void setInterviewType(InterviewType interviewType) { this.interviewType = interviewType; }

    public InterviewStatus getStatus() { return status; }
    public void setStatus(InterviewStatus status) { this.status = status; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }

    public String getInterviewTemplate() { return interviewTemplate; }
    public void setInterviewTemplate(String interviewTemplate) { this.interviewTemplate = interviewTemplate; }

    public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
    public void setMaxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; }

    public String getInterviewContext() { return interviewContext; }
    public void setInterviewContext(String interviewContext) { this.interviewContext = interviewContext; }

    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getPositionTitle() { return positionTitle; }
    public void setPositionTitle(String positionTitle) { this.positionTitle = positionTitle; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getInterviewLink() { return interviewLink; }
    public void setInterviewLink(String interviewLink) { this.interviewLink = interviewLink; }
}

