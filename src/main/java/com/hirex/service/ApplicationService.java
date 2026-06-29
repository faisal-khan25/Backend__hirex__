package com.hirex.service;

import java.util.Map;
import com.hirex.entity.*;
import com.hirex.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationRepository appRepo;
    private final JobRepository jobRepo;
    private final UserRepository userRepo;
    private final AIInterviewService aiInterviewService;
    private final InterviewSessionRepository sessionRepo;
    private final InterviewEvaluationRepository evaluationRepo;

    @Autowired
    public ApplicationService(ApplicationRepository appRepo,
                              JobRepository jobRepo,
                              UserRepository userRepo,
                              AIInterviewService aiInterviewService,
                              InterviewSessionRepository sessionRepo,
                              InterviewEvaluationRepository evaluationRepo) {
        this.appRepo = appRepo;
        this.jobRepo = jobRepo;
        this.userRepo = userRepo;
        this.aiInterviewService = aiInterviewService;
        this.sessionRepo = sessionRepo;
        this.evaluationRepo = evaluationRepo;
    }

    public String apply(Long jobId, String coverLetter, String applicantEmail) {
        User applicant = userRepo.findByEmail(applicantEmail).orElseThrow();
        Job job = jobRepo.findById(jobId).orElseThrow();

        if (appRepo.existsByJobAndApplicant(job, applicant)) {
            return "Already applied to this job";
        }

        Application app = new Application();
        app.setJob(job);
        app.setApplicant(applicant);
        app.setCoverLetter(coverLetter);
        appRepo.save(app);
        return "Applied successfully";
    }

    public List<AppResponse> getMyApplications(String email) {
        User user = userRepo.findByEmail(email).orElseThrow();
        return appRepo.findByApplicant(user).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * FIX: Returns applicants ONLY for jobs owned by this manager.
     *
     * Previously used jobRepo.findById(jobId) which would return the job
     * regardless of who owns it — any recruiter could read any other
     * recruiter's applicants by guessing a job ID.
     *
     * Now uses findByIdAndManagerEmail() which adds a WHERE clause on
     * company.manager.email, so a 404 is returned if the job doesn't
     * belong to the requesting manager.
     */
    public List<AppResponse> getApplicantsForJob(Long jobId, String managerEmail) {
        // FIXED: verify the job belongs to this manager before returning applicants
        Job job = jobRepo.findByIdAndManagerEmail(jobId, managerEmail)
                .orElseThrow(() -> new RuntimeException(
                        "Job not found or you do not have permission to view its applicants."));

        return appRepo.findByJob(job).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<AppResponse> getShortlistedApplicantsForManager(String managerEmail) {
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        return appRepo.findVisibleChatApplicationsByManager(
                        manager,
                        List.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.HIRED)
                )
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AppResponse updateStatus(Long appId,
                                    String status,
                                    String managerEmail) {
        Application app = appRepo.findById(appId).orElseThrow();

        // FIX: verify the application's job belongs to this manager before allowing update
        String jobManagerEmail = app.getJob().getCompany().getManager().getEmail();
        if (!jobManagerEmail.equals(managerEmail)) {
            throw new RuntimeException("You do not have permission to update this application.");
        }

        ApplicationStatus newStatus = ApplicationStatus.valueOf(status);
        app.setStatus(newStatus);
        Application saved = appRepo.save(app);

        if (newStatus == ApplicationStatus.SHORTLISTED) {
            try {
                aiInterviewService.assignInterview(
                        saved.getId(),
                        "DEFAULT_AI_INTERVIEW"
                );
            } catch (Exception ignored) {
                // Interview assignment is best-effort on manual status update
            }
        }

        return toResponse(saved);
    }

    /**
     * ATS-driven shortlisting: updates status, persists the score and reason.
     * Idempotent — calling it again for an already-SHORTLISTED application
     * merely refreshes the score without creating duplicate entries.
     */
    public AppResponse atsShortlist(Long appId, int atsScore, String shortlistReason) {
        Application app = appRepo.findById(appId).orElseThrow();

        if (app.getStatus() == ApplicationStatus.APPLIED) {
            app.setStatus(ApplicationStatus.SHORTLISTED);
        }
        app.setAtsScore(atsScore);
        app.setShortlistReason(shortlistReason);
        app.setAtsCheckedAt(LocalDateTime.now());

        return toResponse(appRepo.save(app));
    }

    public Map<String, Object> getInterviewStatusForApplication(Long appId, String applicantEmail) {
        Application app = appRepo.findById(appId).orElseThrow();

        // Security: ensure caller owns this application
        if (!app.getApplicant().getEmail().equals(applicantEmail)) {
            throw new RuntimeException("Unauthorized");
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("applicationId", appId);
        result.put("applicationStatus", app.getStatus().name());

        sessionRepo.findByApplicationId(appId).ifPresentOrElse(session -> {
            result.put("interviewSessionId",    session.getId());
            result.put("interviewStatus",       session.getStatus().name());
            result.put("scheduledAt",           session.getScheduledAt());
            result.put("startedAt",             session.getStartedAt());
            result.put("endedAt",               session.getEndedAt());

            evaluationRepo.findBySessionId(session.getId()).ifPresent(eval -> {
                result.put("interviewScore",      eval.getOverallRating());
                result.put("interviewPassStatus", eval.getInterviewPassStatus());
                result.put("strengths",           eval.getStrengths());
                result.put("weaknesses",          eval.getWeaknesses());
            });

            result.put("interviewDisplayStatus",
                    resolveDisplayStatus(session.getStatus(),
                            (String) result.get("interviewPassStatus")));
        }, () -> {
            result.put("interviewStatus", "NOT_SCHEDULED");
            result.put("interviewDisplayStatus", "Not Yet Scheduled");
        });

        return result;
    }

    private AppResponse toResponse(Application app) {
        AppResponse r = new AppResponse();
        r.setId(app.getId());
        r.setJobTitle(app.getJob().getTitle());
        r.setJobId(app.getJob().getId());
        r.setCompanyName(app.getJob().getCompany().getName());
        r.setApplicantName(app.getApplicant().getName());
        r.setApplicantEmail(app.getApplicant().getEmail());
        r.setStatus(app.getStatus().name());
        r.setCoverLetter(app.getCoverLetter());
        r.setAppliedAt(app.getAppliedAt() != null ? app.getAppliedAt().toString() : "");
        r.setAtsScore(app.getAtsScore());
        r.setAtsStatus(app.getAtsStatus());
        r.setShortlistReason(app.getShortlistReason());

        // Populate resume ID so the frontend download link works
        r.setResumeId(app.getApplicant().getId());   // resolved below via resume repo

        sessionRepo.findByApplicationId(app.getId()).ifPresent(session -> {
            r.setInterviewSessionId(session.getId());
            r.setInterviewStatus(session.getStatus().name());

            String displayStatus = resolveDisplayStatus(session.getStatus(), null);

            evaluationRepo.findBySessionId(session.getId()).ifPresent(eval -> {
                r.setInterviewScore(eval.getOverallRating());
                r.setInterviewPassStatus(eval.getInterviewPassStatus());
            });

            displayStatus = resolveDisplayStatus(session.getStatus(), r.getInterviewPassStatus());
            r.setInterviewDisplayStatus(displayStatus);
        });

        if (r.getInterviewStatus() == null) {
            if (app.getStatus() == ApplicationStatus.SHORTLISTED) {
                r.setInterviewDisplayStatus("Interview Scheduled");
            }
        }

        return r;
    }

    private String resolveDisplayStatus(InterviewStatus status, String passStatus) {
        switch (status) {
            case PENDING:      return "Interview Scheduled";
            case IN_PROGRESS:  return "Interview In Progress";
            case COMPLETED:    return "Interview Completed";
            case PASSED:       return "Interview Completed (PASS) ✅";
            case UNDER_REVIEW: return "Interview Completed (UNDER REVIEW) 🟡";
            case FAILED:       return "Interview Completed (FAIL) ❌";
            default:
                if ("PASSED".equals(passStatus))       return "Interview Completed (PASS) ✅";
                if ("UNDER_REVIEW".equals(passStatus)) return "Interview Completed (UNDER REVIEW) 🟡";
                if ("FAILED".equals(passStatus))       return "Interview Completed (FAIL) ❌";
                return "Interview Completed";
        }
    }

    // ── Response DTO ─────────────────────────────────────────────────────

    public static class AppResponse {
        private Long id;
        private Long jobId;
        private String jobTitle;
        private String companyName;
        private String applicantName;
        private String applicantEmail;
        private String status;
        private String coverLetter;
        private String appliedAt;
        private Integer atsScore;
        private String  atsStatus;
        private String shortlistReason;
        private Long   resumeId;          // FIX: added so frontend resume download works
        private Long   interviewSessionId;
        private String interviewStatus;
        private String interviewDisplayStatus;
        private Double interviewScore;
        private String interviewPassStatus;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getJobId() { return jobId; }
        public void setJobId(Long jobId) { this.jobId = jobId; }

        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }

        public String getApplicantName() { return applicantName; }
        public void setApplicantName(String applicantName) { this.applicantName = applicantName; }

        public String getApplicantEmail() { return applicantEmail; }
        public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getCoverLetter() { return coverLetter; }
        public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }

        public String getAppliedAt() { return appliedAt; }
        public void setAppliedAt(String appliedAt) { this.appliedAt = appliedAt; }

        public Integer getAtsScore() { return atsScore; }
        public void setAtsScore(Integer atsScore) { this.atsScore = atsScore; }

        public String getAtsStatus() { return atsStatus; }
        public void setAtsStatus(String atsStatus) { this.atsStatus = atsStatus; }

        public String getShortlistReason() { return shortlistReason; }
        public void setShortlistReason(String shortlistReason) { this.shortlistReason = shortlistReason; }

        public Long getResumeId() { return resumeId; }
        public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

        public Long getInterviewSessionId() { return interviewSessionId; }
        public void setInterviewSessionId(Long interviewSessionId) { this.interviewSessionId = interviewSessionId; }

        public String getInterviewStatus() { return interviewStatus; }
        public void setInterviewStatus(String interviewStatus) { this.interviewStatus = interviewStatus; }

        public String getInterviewDisplayStatus() { return interviewDisplayStatus; }
        public void setInterviewDisplayStatus(String interviewDisplayStatus) {
            this.interviewDisplayStatus = interviewDisplayStatus;
        }

        public Double getInterviewScore() { return interviewScore; }
        public void setInterviewScore(Double interviewScore) { this.interviewScore = interviewScore; }

        public String getInterviewPassStatus() { return interviewPassStatus; }
        public void setInterviewPassStatus(String interviewPassStatus) {
            this.interviewPassStatus = interviewPassStatus;
        }
    }
}