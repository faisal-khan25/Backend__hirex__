package com.hirex.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * LiveInterviewSession — UPDATED for Multi-Applicant Assignment
 *
 * NEW CHANGE:
 *   Added `assignedApplicantIds` — a Set<Long> stored in a join table
 *   `live_interview_assigned_applicants`. The recruiter explicitly assigns
 *   one or more applicant (User) IDs when creating a live session.
 *
 *   The `candidate` field is kept for backward-compatibility with the
 *   WebRTC signaling logic (1-on-1 session once a candidate actually joins).
 *   It is set to the first/only applicant at creation time when there is
 *   exactly one assignee, or set dynamically when a candidate joins.
 */
@Entity
@Table(
        name = "live_interview_sessions",
        indexes = {
                @Index(name = "idx_live_session_token",     columnList = "session_token"),
                @Index(name = "idx_live_session_recruiter", columnList = "recruiter_id"),
                @Index(name = "idx_live_session_candidate", columnList = "candidate_id"),
                @Index(name = "idx_live_session_status",    columnList = "session_status")
        }
)
public class LiveInterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Link to the parent AI/scheduled interview session ────────────────
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_session_id", nullable = false, unique = true)
    private InterviewSession interviewSession;

    // ── Participants ──────────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private User recruiter;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    // ── NEW: Explicitly assigned applicant IDs ────────────────────────────
    /**
     * The set of User IDs (applicants) the recruiter has assigned to this
     * live interview session. Only these users will see the "Join Live Interview"
     * button in the jobseeker panel.
     *
     * Stored in a separate join table to keep the main table clean.
     * At least one ID must be present (set at creation time).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "live_interview_assigned_applicants",
            joinColumns = @JoinColumn(name = "live_session_id")
    )
    @Column(name = "applicant_id")
    private Set<Long> assignedApplicantIds = new HashSet<>();

    // ── Security tokens ───────────────────────────────────────────────────
    @Column(name = "session_token", nullable = false, unique = true, length = 64)
    private String sessionToken;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    // ── Timing ────────────────────────────────────────────────────────────
    @Column(name = "interview_start_time")
    private LocalDateTime interviewStartTime;

    @Column(name = "interview_end_time")
    private LocalDateTime interviewEndTime;

    // ── Candidate camera state ────────────────────────────────────────────
    @Column(name = "camera_enabled", nullable = false)
    private boolean cameraEnabled = false;

    @Column(name = "camera_disabled_at")
    private LocalDateTime cameraDisabledAt;

    @Column(name = "camera_off_count", nullable = false)
    private int cameraOffCount = 0;

    // ── Recruiter camera state ────────────────────────────────────────────
    @Column(name = "recruiter_camera_enabled", nullable = false)
    private boolean recruiterCameraEnabled = false;

    @Column(name = "recruiter_camera_disabled_at")
    private LocalDateTime recruiterCameraDisabledAt;

    @Column(name = "recruiter_camera_off_count", nullable = false)
    private int recruiterCameraOffCount = 0;

    // ── Recruiter end-of-interview notes ─────────────────────────────────
    @Column(name = "recruiter_notes", columnDefinition = "TEXT")
    private String recruiterNotes;

    // ── Session lifecycle ─────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false)
    private LiveSessionStatus sessionStatus = LiveSessionStatus.WAITING;

    // ── Audit ─────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Lifecycle callbacks ───────────────────────────────────────────────
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (tokenExpiresAt == null) {
            tokenExpiresAt = createdAt.plusHours(24);
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────
    public LiveInterviewSession() {}

    public LiveInterviewSession(InterviewSession interviewSession,
                                User recruiter,
                                User candidate,
                                String sessionToken,
                                Set<Long> assignedApplicantIds) {
        this.interviewSession      = interviewSession;
        this.recruiter             = recruiter;
        this.candidate             = candidate;
        this.sessionToken          = sessionToken;
        this.sessionStatus         = LiveSessionStatus.WAITING;
        this.tokenExpiresAt        = LocalDateTime.now().plusHours(24);
        this.assignedApplicantIds  = assignedApplicantIds != null
                ? new HashSet<>(assignedApplicantIds)
                : new HashSet<>();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InterviewSession getInterviewSession() { return interviewSession; }
    public void setInterviewSession(InterviewSession interviewSession) { this.interviewSession = interviewSession; }

    public User getRecruiter() { return recruiter; }
    public void setRecruiter(User recruiter) { this.recruiter = recruiter; }

    public User getCandidate() { return candidate; }
    public void setCandidate(User candidate) { this.candidate = candidate; }

    public Set<Long> getAssignedApplicantIds() { return assignedApplicantIds; }
    public void setAssignedApplicantIds(Set<Long> assignedApplicantIds) {
        this.assignedApplicantIds = assignedApplicantIds != null ? assignedApplicantIds : new HashSet<>();
    }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public LocalDateTime getInterviewStartTime() { return interviewStartTime; }
    public void setInterviewStartTime(LocalDateTime interviewStartTime) { this.interviewStartTime = interviewStartTime; }

    public LocalDateTime getInterviewEndTime() { return interviewEndTime; }
    public void setInterviewEndTime(LocalDateTime interviewEndTime) { this.interviewEndTime = interviewEndTime; }

    public boolean isCameraEnabled() { return cameraEnabled; }
    public void setCameraEnabled(boolean cameraEnabled) { this.cameraEnabled = cameraEnabled; }

    public LocalDateTime getCameraDisabledAt() { return cameraDisabledAt; }
    public void setCameraDisabledAt(LocalDateTime cameraDisabledAt) { this.cameraDisabledAt = cameraDisabledAt; }

    public int getCameraOffCount() { return cameraOffCount; }
    public void setCameraOffCount(int cameraOffCount) { this.cameraOffCount = cameraOffCount; }

    public boolean isRecruiterCameraEnabled() { return recruiterCameraEnabled; }
    public void setRecruiterCameraEnabled(boolean recruiterCameraEnabled) { this.recruiterCameraEnabled = recruiterCameraEnabled; }

    public LocalDateTime getRecruiterCameraDisabledAt() { return recruiterCameraDisabledAt; }
    public void setRecruiterCameraDisabledAt(LocalDateTime recruiterCameraDisabledAt) { this.recruiterCameraDisabledAt = recruiterCameraDisabledAt; }

    public int getRecruiterCameraOffCount() { return recruiterCameraOffCount; }
    public void setRecruiterCameraOffCount(int recruiterCameraOffCount) { this.recruiterCameraOffCount = recruiterCameraOffCount; }

    public String getRecruiterNotes() { return recruiterNotes; }
    public void setRecruiterNotes(String recruiterNotes) { this.recruiterNotes = recruiterNotes; }

    public LiveSessionStatus getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(LiveSessionStatus sessionStatus) { this.sessionStatus = sessionStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}