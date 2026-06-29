package com.hirex.dto;

import com.hirex.entity.LiveSessionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * All DTOs used by the Live Camera Monitoring feature.
 *
 * UPDATED (Multi-Applicant Assignment):
 *   CreateLiveSessionRequest now accepts `assignedApplicantIds` — a list of
 *   applicant User IDs the recruiter explicitly assigns to this live session.
 *   At least one ID is required. This replaces the old implicit single-candidate
 *   derivation and allows the recruiter to invite multiple applicants.
 *
 *   LiveSessionResponse now returns `assignedApplicantIds` so the jobseeker
 *   panel can confirm the logged-in user is assigned before showing the button.
 */
public class LiveInterviewDto {

    // =========================================================================
    // 1.  REST – Create / Join
    // =========================================================================

    public static class CreateLiveSessionRequest {
        // interviewSessionId OR jobId must be provided (interviewSessionId takes priority)
        private Long interviewSessionId;

        /**
         * Alternative to interviewSessionId: recruiter can start a live session
         * directly from a job without needing a prior AI interview session.
         * If provided and interviewSessionId is null, the backend will auto-assign
         * an interview session for the primary applicant (or create one on-the-fly).
         */
        private Long jobId;

        /**
         * NEW: Explicit list of applicant User IDs to assign to this live session.
         * The recruiter selects these from the applicant list in the UI.
         * At least one ID must be provided.
         * Replaces the old implicit single-candidate derivation from interview.application.
         */
        private List<Long> assignedApplicantIds;

        // kept for backward-compat / fallback (optional, ignored if assignedApplicantIds is set)
        private Long candidateId;

        public Long getInterviewSessionId() { return interviewSessionId; }
        public void setInterviewSessionId(Long interviewSessionId) { this.interviewSessionId = interviewSessionId; }

        public Long getJobId() { return jobId; }
        public void setJobId(Long jobId) { this.jobId = jobId; }

        public List<Long> getAssignedApplicantIds() { return assignedApplicantIds; }
        public void setAssignedApplicantIds(List<Long> assignedApplicantIds) { this.assignedApplicantIds = assignedApplicantIds; }

        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    }

    /**
     * Full response – includes sessionToken and assignedApplicantIds.
     * Only returned by POST /create and GET /join/{token}.
     */
    public static class LiveSessionResponse {
        private Long liveSessionId;
        private String sessionToken;
        private Long recruiterId;
        private String recruiterName;
        private Long candidateId;
        private String candidateName;
        private LiveSessionStatus sessionStatus;
        private boolean cameraEnabled;
        private boolean recruiterCameraEnabled;
        private LocalDateTime createdAt;
        private LocalDateTime interviewStartTime;
        private LocalDateTime interviewEndTime;
        private Long durationSeconds;
        private String recruiterNotes;
        private String companyName;
        private String jobTitle;

        /** NEW: the set of assigned applicant IDs — jobseeker panel checks this */
        private Set<Long> assignedApplicantIds;

        public Long getLiveSessionId() { return liveSessionId; }
        public void setLiveSessionId(Long liveSessionId) { this.liveSessionId = liveSessionId; }

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public Long getRecruiterId() { return recruiterId; }
        public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }

        public String getRecruiterName() { return recruiterName; }
        public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }

        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }

        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

        public LiveSessionStatus getSessionStatus() { return sessionStatus; }
        public void setSessionStatus(LiveSessionStatus sessionStatus) { this.sessionStatus = sessionStatus; }

        public boolean isCameraEnabled() { return cameraEnabled; }
        public void setCameraEnabled(boolean cameraEnabled) { this.cameraEnabled = cameraEnabled; }

        public boolean isRecruiterCameraEnabled() { return recruiterCameraEnabled; }
        public void setRecruiterCameraEnabled(boolean recruiterCameraEnabled) { this.recruiterCameraEnabled = recruiterCameraEnabled; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getInterviewStartTime() { return interviewStartTime; }
        public void setInterviewStartTime(LocalDateTime interviewStartTime) { this.interviewStartTime = interviewStartTime; }

        public LocalDateTime getInterviewEndTime() { return interviewEndTime; }
        public void setInterviewEndTime(LocalDateTime interviewEndTime) { this.interviewEndTime = interviewEndTime; }

        public Long getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

        public String getRecruiterNotes() { return recruiterNotes; }
        public void setRecruiterNotes(String recruiterNotes) { this.recruiterNotes = recruiterNotes; }

        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

        public Set<Long> getAssignedApplicantIds() { return assignedApplicantIds; }
        public void setAssignedApplicantIds(Set<Long> assignedApplicantIds) { this.assignedApplicantIds = assignedApplicantIds; }
    }

    /**
     * Summary response WITHOUT sessionToken (safe for listing endpoints).
     */
    public static class LiveSessionSummaryResponse {
        private Long liveSessionId;
        private Long recruiterId;
        private String recruiterName;
        private Long candidateId;
        private String candidateName;
        private LiveSessionStatus sessionStatus;
        private boolean cameraEnabled;
        private boolean recruiterCameraEnabled;
        private LocalDateTime createdAt;
        private LocalDateTime interviewStartTime;
        private LocalDateTime interviewEndTime;
        private Long durationSeconds;

        /** NEW: the set of assigned applicant IDs */
        private Set<Long> assignedApplicantIds;

        public Long getLiveSessionId() { return liveSessionId; }
        public void setLiveSessionId(Long liveSessionId) { this.liveSessionId = liveSessionId; }

        public Long getRecruiterId() { return recruiterId; }
        public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }

        public String getRecruiterName() { return recruiterName; }
        public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }

        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }

        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

        public LiveSessionStatus getSessionStatus() { return sessionStatus; }
        public void setSessionStatus(LiveSessionStatus sessionStatus) { this.sessionStatus = sessionStatus; }

        public boolean isCameraEnabled() { return cameraEnabled; }
        public void setCameraEnabled(boolean cameraEnabled) { this.cameraEnabled = cameraEnabled; }

        public boolean isRecruiterCameraEnabled() { return recruiterCameraEnabled; }
        public void setRecruiterCameraEnabled(boolean recruiterCameraEnabled) { this.recruiterCameraEnabled = recruiterCameraEnabled; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getInterviewStartTime() { return interviewStartTime; }
        public void setInterviewStartTime(LocalDateTime interviewStartTime) { this.interviewStartTime = interviewStartTime; }

        public LocalDateTime getInterviewEndTime() { return interviewEndTime; }
        public void setInterviewEndTime(LocalDateTime interviewEndTime) { this.interviewEndTime = interviewEndTime; }

        public Long getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

        public Set<Long> getAssignedApplicantIds() { return assignedApplicantIds; }
        public void setAssignedApplicantIds(Set<Long> assignedApplicantIds) { this.assignedApplicantIds = assignedApplicantIds; }
    }

    // =========================================================================
    // NEW: Applicant real-time notification payload
    // =========================================================================

    /**
     * Sent to each assigned applicant via /user/queue/live-interview-invite
     * immediately when the recruiter creates a live session.
     */
    public static class LiveInterviewNotification {
        private Long   liveSessionId;
        private String sessionToken;
        private String recruiterName;
        private String companyName;
        private String jobTitle;
        private String type;   // "INVITE" | "ENDED" | "CANCELLED"
        private LocalDateTime timestamp;

        public Long getLiveSessionId() { return liveSessionId; }
        public void setLiveSessionId(Long liveSessionId) { this.liveSessionId = liveSessionId; }

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getRecruiterName() { return recruiterName; }
        public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }

        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    // =========================================================================
    // 2.  WebRTC Signaling messages (UNCHANGED)
    // =========================================================================

    public static class WebRtcOfferMessage {
        @NotBlank(message = "sessionToken is required")
        private String sessionToken;

        @NotBlank(message = "senderRole is required")
        private String senderRole;

        @NotBlank(message = "sdp is required")
        private String sdp;

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getSenderRole() { return senderRole; }
        public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

        public String getSdp() { return sdp; }
        public void setSdp(String sdp) { this.sdp = sdp; }
    }

    public static class WebRtcAnswerMessage {
        @NotBlank(message = "sessionToken is required")
        private String sessionToken;

        @NotBlank(message = "senderRole is required")
        private String senderRole;

        @NotBlank(message = "sdp is required")
        private String sdp;

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getSenderRole() { return senderRole; }
        public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

        public String getSdp() { return sdp; }
        public void setSdp(String sdp) { this.sdp = sdp; }
    }

    public static class IceCandidateMessage {
        @NotBlank(message = "sessionToken is required")
        private String sessionToken;

        @NotBlank(message = "senderRole is required")
        private String senderRole;

        private String candidate;
        private String sdpMid;
        private Integer sdpMLineIndex;

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getSenderRole() { return senderRole; }
        public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

        public String getCandidate() { return candidate; }
        public void setCandidate(String candidate) { this.candidate = candidate; }

        public String getSdpMid() { return sdpMid; }
        public void setSdpMid(String sdpMid) { this.sdpMid = sdpMid; }

        public Integer getSdpMLineIndex() { return sdpMLineIndex; }
        public void setSdpMLineIndex(Integer sdpMLineIndex) { this.sdpMLineIndex = sdpMLineIndex; }
    }

    public static class JoinSessionMessage {
        @NotBlank(message = "sessionToken is required")
        private String sessionToken;

        @NotBlank(message = "role is required")
        private String role;

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class CameraStatusMessage {
        @NotBlank(message = "sessionToken is required")
        private String sessionToken;

        @NotBlank(message = "role is required")
        private String role;

        private boolean cameraEnabled;
        private String reason;

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public boolean isCameraEnabled() { return cameraEnabled; }
        public void setCameraEnabled(boolean cameraEnabled) { this.cameraEnabled = cameraEnabled; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // =========================================================================
    // 3.  WebSocket broadcast/unicast payloads (UNCHANGED)
    // =========================================================================

    public static class SessionStatusNotification {
        private Long liveSessionId;
        private LiveSessionStatus sessionStatus;
        private String message;
        private LocalDateTime timestamp;

        public Long getLiveSessionId() { return liveSessionId; }
        public void setLiveSessionId(Long liveSessionId) { this.liveSessionId = liveSessionId; }

        public LiveSessionStatus getSessionStatus() { return sessionStatus; }
        public void setSessionStatus(LiveSessionStatus sessionStatus) { this.sessionStatus = sessionStatus; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class CameraStatusNotification {
        private Long liveSessionId;
        private String participantRole;
        private boolean cameraEnabled;
        private String reason;
        private int cameraOffCount;
        private LocalDateTime timestamp;

        public Long getLiveSessionId() { return liveSessionId; }
        public void setLiveSessionId(Long liveSessionId) { this.liveSessionId = liveSessionId; }

        public String getParticipantRole() { return participantRole; }
        public void setParticipantRole(String participantRole) { this.participantRole = participantRole; }

        public boolean isCameraEnabled() { return cameraEnabled; }
        public void setCameraEnabled(boolean cameraEnabled) { this.cameraEnabled = cameraEnabled; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public int getCameraOffCount() { return cameraOffCount; }
        public void setCameraOffCount(int cameraOffCount) { this.cameraOffCount = cameraOffCount; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    // =========================================================================
    // 4.  ICE Server config
    // =========================================================================

    public static class IceServerConfig {
        private java.util.List<String> urls;
        private String username;
        private String credential;

        public IceServerConfig(String url) {
            this.urls = java.util.List.of(url);
        }

        public java.util.List<String> getUrls() { return urls; }
        public void setUrls(java.util.List<String> urls) { this.urls = urls; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getCredential() { return credential; }
        public void setCredential(String credential) { this.credential = credential; }
    }

    // =========================================================================
    // 5.  End interview request
    // =========================================================================

    public static class EndInterviewRequest {
        @NotBlank(message = "sessionToken is required")
        private String sessionToken;

        private String notes;

        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}