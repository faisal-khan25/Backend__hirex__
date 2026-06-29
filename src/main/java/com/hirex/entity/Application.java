package com.hirex.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * OPTIMIZED: Added indexes on applicant_id and status for faster lookups
 * used by chat conversation queries and application listing.
 */
@Entity
@Table(
    name = "applications",
    indexes = {
        @Index(name = "idx_app_applicant_id", columnList = "applicant_id"),
        @Index(name = "idx_app_job_id",       columnList = "job_id"),
        @Index(name = "idx_app_status",        columnList = "status"),
        // Compound index for manager's shortlisted-applications query
        @Index(name = "idx_app_job_status",    columnList = "job_id, status")
    }
)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne
    @JoinColumn(name = "applicant_id")
    private User applicant;

    private String shortlistSource;
    private LocalDateTime shortlistedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ApplicationStatus status;

    private String coverLetter;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    private Integer atsScore;

    // "SHORTLISTED" or "REJECTED" — set by job-specific ATS
    @Column(name = "ats_status", length = 20)
    private String atsStatus;

    @Column(length = 1000)
    private String shortlistReason;

    private LocalDateTime atsCheckedAt;

    @PrePersist
    public void prePersist() {
        appliedAt = LocalDateTime.now();
        if (status == null) {
            status = ApplicationStatus.APPLIED;
        }
    }

    public Application() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public User getApplicant() { return applicant; }
    public void setApplicant(User applicant) { this.applicant = applicant; }

    public String getShortlistSource() { return shortlistSource; }
    public void setShortlistSource(String shortlistSource) { this.shortlistSource = shortlistSource; }

    public LocalDateTime getShortlistedAt() { return shortlistedAt; }
    public void setShortlistedAt(LocalDateTime shortlistedAt) { this.shortlistedAt = shortlistedAt; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }

    public Integer getAtsScore() { return atsScore; }
    public void setAtsScore(Integer atsScore) { this.atsScore = atsScore; }

    public String getShortlistReason() { return shortlistReason; }
    public void setShortlistReason(String shortlistReason) { this.shortlistReason = shortlistReason; }

    public String getAtsStatus() { return atsStatus; }
    public void setAtsStatus(String atsStatus) { this.atsStatus = atsStatus; }

    public LocalDateTime getAtsCheckedAt() { return atsCheckedAt; }
    public void setAtsCheckedAt(LocalDateTime atsCheckedAt) { this.atsCheckedAt = atsCheckedAt; }
}
