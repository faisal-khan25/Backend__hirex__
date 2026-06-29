package com.hirex.service;



import com.hirex.dto.AtsResultDto;
import com.hirex.entity.*;
import com.hirex.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates ATS evaluation → auto-shortlisting → conversation seeding.
 *
 * Flow:
 *   1. Run ATS scoring on the resume.
 *   2. If score ≥ threshold AND candidate has an application for this manager's jobs:
 *      a. Mark application SHORTLISTED (idempotent – skip if already shortlisted).
 *      b. Record atsScore, shortlistReason, shortlistSource = "ATS", shortlistedAt.
 *      c. Seed a welcome system message into the conversation (only once).
 *   3. Return enriched AtsResultDto with shortlist outcome details.
 */
@Service
public class AtsShortlistService {

    static final int ATS_THRESHOLD = 70; // score >= 70 → auto-shortlist

    private final AtsService              atsService;
    private final ResumeRepository        resumeRepo;
    private final ApplicationRepository   appRepo;
    private final ChatMessageRepository   chatRepo;
    private final UserRepository          userRepo;

    public AtsShortlistService(AtsService atsService,
                               ResumeRepository resumeRepo,
                               ApplicationRepository appRepo,
                               ChatMessageRepository chatRepo,
                               UserRepository userRepo) {
        this.atsService  = atsService;
        this.resumeRepo  = resumeRepo;
        this.appRepo     = appRepo;
        this.chatRepo    = chatRepo;
        this.userRepo    = userRepo;
    }

    /**
     * Run ATS on resume {resumeId}, then auto-shortlist if eligible.
     * @param resumeId  the resume to evaluate
     * @param managerEmail  the authenticated manager
     */
    @Transactional
    public AtsResultDto evaluateAndShortlist(Long resumeId, String managerEmail) {
        Resume resume = resumeRepo.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found: " + resumeId));

        User manager = userRepo.findByEmail(managerEmail)
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        // 1. Run ATS scoring
        AtsResultDto result = atsService.check(resume.getResumeText());

        User candidate = resume.getUser();

        // 2. Find the candidate's application(s) under this manager's jobs
        List<Application> apps =
                appRepo.findShortlistedApplicationsByManager(
                        manager,
                        ApplicationStatus.SHORTLISTED
                );


        if (apps.isEmpty()) {
            // Candidate hasn't applied to any of this manager's jobs — score only
            result.setShortlistMessage("Candidate has not applied to any of your jobs. ATS score recorded.");
            return result;
        }

        // Pick the most relevant application (prefer APPLIED or SHORTLISTED; take latest)
        Application app = apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.APPLIED || a.getStatus() == ApplicationStatus.SHORTLISTED)
                .reduce((a, b) -> a.getAppliedAt().isAfter(b.getAppliedAt()) ? a : b)
                .orElse(apps.get(0));

        result.setApplicationId(app.getId());
        result.setCandidateName(candidate.getName());
        result.setJobTitle(app.getJob().getTitle());

        // 3. Persist ATS score on application regardless of threshold
        app.setAtsScore(result.getAtsScore());

        if (result.getAtsScore() >= ATS_THRESHOLD) {
            boolean wasAlreadyShortlisted = app.getStatus() == ApplicationStatus.SHORTLISTED
                    && "ATS".equals(app.getShortlistSource());

            if (wasAlreadyShortlisted) {
                // Idempotent — already ATS-shortlisted
                result.setAlreadyShortlisted(true);
                result.setAutoShortlisted(false);
                result.setShortlistMessage(
                        candidate.getName() + " was already auto-shortlisted by ATS with score "
                                + app.getAtsScore() + ". Conversation thread is active.");
            } else {
                // Auto-shortlist now
                String reason = buildShortlistReason(result);
                app.setStatus(ApplicationStatus.SHORTLISTED);
                app.setShortlistSource("ATS");
                app.setShortlistReason(reason);
//                app.setShortlistedAt(LocalDateTime.now());
                appRepo.save(app);

                // Seed welcome message from manager into the conversation (once)
                seedWelcomeMessage(app, manager);

                result.setAutoShortlisted(true);
                result.setAlreadyShortlisted(false);
                result.setShortlistMessage(
                        "🎉 " + candidate.getName() + " has been automatically shortlisted! "
                                + "ATS score: " + result.getAtsScore() + "/100. "
                                + "A conversation thread has been created in the Conversations tab.");
            }
        } else {
            // Below threshold — not shortlisted
            appRepo.save(app); // still save the ATS score
            result.setAutoShortlisted(false);
            result.setAlreadyShortlisted(false);
            result.setShortlistMessage(
                    "Score " + result.getAtsScore() + "/100 is below the threshold of "
                            + ATS_THRESHOLD + ". Candidate not shortlisted.");
        }

        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String buildShortlistReason(AtsResultDto result) {
        int matched = result.getMatchedKeywords() != null ? result.getMatchedKeywords().size() : 0;
        int total   = matched + (result.getMissingKeywords() != null ? result.getMissingKeywords().size() : 0);
        String kws  = result.getMatchedKeywords() != null
                ? String.join(", ", result.getMatchedKeywords())
                : "";
        return String.format(
                "ATS score %d/100 (threshold %d). Matched %d/%d keywords: %s.",
                result.getAtsScore(), ATS_THRESHOLD, matched, total, kws
        );
    }

    /**
     * Seeds one automated welcome message into the chat from the manager,
     * but only if no messages exist yet for this application (prevents duplicates).
     */
    private void seedWelcomeMessage(Application app, User manager) {
        // Check if any messages already exist
        List<?> existing = chatRepo.findByApplicationOrderBySentAtAsc(app);
        if (!existing.isEmpty()) return; // conversation already has content

        ChatMessage welcome = new ChatMessage();
        welcome.setApplication(app);
        welcome.setSender(manager);
        welcome.setContent(
                "👋 Hi " + app.getApplicant().getName() + "! Congratulations — your resume for the \""
                        + app.getJob().getTitle() + "\" position has been reviewed and you have been shortlisted "
                        + "based on your strong ATS score (" + app.getAtsScore() + "/100). "
                        + "We'd love to discuss the next steps with you. Please feel free to ask any questions "
                        + "about the role, interview process, or required documents."
        );
        chatRepo.save(welcome);
    }
}
