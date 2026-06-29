package com.hirex.service;

import com.hirex.dto.BulkInterviewAssignResponseDto;
import com.hirex.entity.*;
import com.hirex.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * BulkInterviewAssignService
 *
 * FIX 1: The original findShortlistedByJobId() query filters on atsStatus = 'SHORTLISTED',
 *         but AIInterviewService.assignInterview() requires application.status == SHORTLISTED
 *         (the enum field). These are TWO different fields:
 *           - atsStatus  (String) — set by the ATS run
 *           - status     (ApplicationStatus enum) — the main application status
 *         After ATS runs, only atsStatus is updated. The status enum stays 'APPLIED'.
 *         So assignInterview() always threw "Application must be SHORTLISTED before assigning".
 *
 *         FIX: In createSession() we now set app.status = SHORTLISTED before creating the
 *         session, so both fields are consistent and the check in assignInterview() passes.
 *
 * FIX 2: We no longer call AIInterviewService.assignInterview() from here — that method
 *         has its own status guard. Instead, BulkInterviewAssignService creates the
 *         InterviewSession directly (same as assignAllForJob was already doing), avoiding
 *         the double-guard problem entirely.
 *
 * FIX 3: Added @Transactional to createSession so any DB failure rolls back cleanly.
 */
@Service
public class BulkInterviewAssignService {

    private static final Logger log = LoggerFactory.getLogger(BulkInterviewAssignService.class);

    private final ApplicationRepository      appRepo;
    private final JobRepository              jobRepo;
    private final InterviewSessionRepository sessionRepo;

    public BulkInterviewAssignService(ApplicationRepository appRepo,
                                      JobRepository jobRepo,
                                      InterviewSessionRepository sessionRepo) {
        this.appRepo     = appRepo;
        this.jobRepo     = jobRepo;
        this.sessionRepo = sessionRepo;
    }

    // ── Assign to one applicant ───────────────────────────────────────────

    @Transactional
    public BulkInterviewAssignResponseDto assignToOne(Long applicationId) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        if (sessionRepo.existsByApplicationId(applicationId)) {
            return BulkInterviewAssignResponseDto.builder()
                    .jobId(app.getJob().getId())
                    .jobTitle(app.getJob().getTitle())
                    .assigned(0)
                    .skipped(1)
                    .alreadyAssigned(1)
                    .success(true)
                    .message("Interview already assigned to " + app.getApplicant().getName())
                    .build();
        }

        createSession(app);

        return BulkInterviewAssignResponseDto.builder()
                .jobId(app.getJob().getId())
                .jobTitle(app.getJob().getTitle())
                .assigned(1)
                .skipped(0)
                .alreadyAssigned(0)
                .success(true)
                .message("AI Interview assigned to " + app.getApplicant().getName())
                .build();
    }

    // ── Assign to all shortlisted for a job ──────────────────────────────

    @Transactional
    public BulkInterviewAssignResponseDto assignAllForJob(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        // FIX: Also repair any applications shortlisted before the JobAtsService
        // status-sync fix was applied (i.e. atsStatus='SHORTLISTED' but no session yet).
        repairMissedSessions(jobId);

        // FIX: findShortlistedByJobId filters on atsStatus = 'SHORTLISTED'.
        // We use this list but also sync the status enum before creating the session.
        List<Application> shortlisted = appRepo.findShortlistedByJobId(jobId);

        if (shortlisted.isEmpty()) {
            return BulkInterviewAssignResponseDto.builder()
                    .jobId(jobId)
                    .jobTitle(job.getTitle())
                    .assigned(0)
                    .skipped(0)
                    .alreadyAssigned(0)
                    .success(false)
                    .message("No shortlisted candidates found for job: " + job.getTitle() +
                            ". Please run ATS Evaluation first.")
                    .build();
        }

        int assigned        = 0;
        int alreadyAssigned = 0;

        for (Application app : shortlisted) {
            if (sessionRepo.existsByApplicationId(app.getId())) {
                alreadyAssigned++;
                log.debug("Skipping application {} — interview already exists", app.getId());
                continue;
            }

            try {
                createSession(app);
                assigned++;
            } catch (Exception e) {
                log.error("Failed to create interview session for application {}: {}",
                        app.getId(), e.getMessage(), e);
            }
        }

        int skipped = alreadyAssigned;
        String msg;
        if (assigned == 0 && alreadyAssigned > 0) {
            msg = "All " + alreadyAssigned + " shortlisted candidates already had interviews assigned.";
        } else if (assigned == 0) {
            msg = "No interviews were assigned. Check server logs for errors.";
        } else {
            msg = String.format("%d AI Interview%s Assigned Successfully. Skipped: %d (Already Assigned: %d)",
                    assigned, assigned == 1 ? "" : "s", skipped, alreadyAssigned);
        }

        return BulkInterviewAssignResponseDto.builder()
                .jobId(jobId)
                .jobTitle(job.getTitle())
                .assigned(assigned)
                .skipped(skipped)
                .alreadyAssigned(alreadyAssigned)
                .success(true)
                .message(msg)
                .build();
    }

    // ── Repair: backfill sessions for already-shortlisted apps ─────────────

    /**
     * FIX for existing data: finds all shortlisted applications for a job that
     * have NO InterviewSession yet (missed because JobAtsService didn't sync
     * app.status before this fix) and creates sessions for them.
     *
     * Called by POST /api/interview/assign-all/{jobId} automatically — so the
     * recruiter doesn't need to do anything special; just clicking "Assign to All"
     * again will repair any missed applications.
     */
    @Transactional
    public BulkInterviewAssignResponseDto repairMissedSessions(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        List<Application> missed = appRepo.findShortlistedWithoutSession(jobId);
        if (missed.isEmpty()) {
            return BulkInterviewAssignResponseDto.builder()
                    .jobId(jobId).jobTitle(job.getTitle())
                    .assigned(0).skipped(0).alreadyAssigned(0)
                    .success(true).message("No missed sessions to repair.")
                    .build();
        }

        int repaired = 0;
        for (Application app : missed) {
            try {
                createSession(app);
                repaired++;
                log.info("Repaired missing session for application {} (candidate: {})",
                        app.getId(), app.getApplicant().getName());
            } catch (Exception e) {
                log.error("Failed to repair session for application {}: {}", app.getId(), e.getMessage(), e);
            }
        }

        return BulkInterviewAssignResponseDto.builder()
                .jobId(jobId).jobTitle(job.getTitle())
                .assigned(repaired).skipped(0).alreadyAssigned(0)
                .success(true)
                .message(String.format("Repaired %d missing interview session(s) for job: %s", repaired, job.getTitle()))
                .build();
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * FIX: Sync app.status to SHORTLISTED before saving the session.
     * This ensures:
     *   (a) The status enum is consistent with atsStatus string.
     *   (b) The candidate appears in Conversations (which filters on status IN [SHORTLISTED, HIRED]).
     *   (c) AIInterviewService.assignInterview()'s status guard won't throw if called later.
     */
    @Transactional
    protected void createSession(Application app) {
        // Sync status enum so Conversations page shows this candidate
        if (app.getStatus() == ApplicationStatus.APPLIED) {
            app.setStatus(ApplicationStatus.SHORTLISTED);
            appRepo.save(app);
        }

        InterviewSession session = new InterviewSession();
        session.setApplication(app);
        session.setInterviewType(InterviewType.AI);
        session.setStatus(InterviewStatus.PENDING);
        session.setScheduledAt(LocalDateTime.now().plusDays(1));
        session.setMaxDurationMinutes(60);
        session.setCandidateName(app.getApplicant().getName());
        session.setPositionTitle(app.getJob().getTitle());
        session.setJobDescription(app.getJob().getDescription());
        session.setInterviewTemplate("DEFAULT_AI_INTERVIEW");
        session.setInterviewLink(generateInterviewLink(app));
        session.setCreatedBy("SYSTEM_ATS");

        sessionRepo.save(session);
        log.info("Interview session created for application {} (candidate: {}, job: {})",
                app.getId(), app.getApplicant().getName(), app.getJob().getTitle());
    }

    private String generateInterviewLink(Application app) {
        String token = UUID.randomUUID().toString().replace("-", "");
        return "/interview/" + token;
    }

}