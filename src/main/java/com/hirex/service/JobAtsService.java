package com.hirex.service;

import com.hirex.dto.AtsResultDto;
import com.hirex.dto.JobAtsResponseDto;
import com.hirex.entity.*;
import com.hirex.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JobAtsService
 *
 * Performs job-specific ATS shortlisting:
 *   1. Fetch the job and its required skills/experience/education.
 *   2. Score ONLY the resumes of candidates who applied to THAT job.
 *   3. Persist atsScore + atsStatus (SHORTLISTED / REJECTED) on each Application.
 *   4. Return a per-job summary with per-candidate results.
 *
 * Threshold: AtsService.ATS_THRESHOLD (default 80)
 */
@Service
public class JobAtsService {

    private static final Logger log = LoggerFactory.getLogger(JobAtsService.class);

    private final ApplicationRepository appRepo;
    private final JobRepository         jobRepo;
    private final ResumeRepository      resumeRepo;
    private final AtsService            atsService;

    public JobAtsService(ApplicationRepository appRepo,
                         JobRepository jobRepo,
                         ResumeRepository resumeRepo,
                         AtsService atsService) {
        this.appRepo    = appRepo;
        this.jobRepo    = jobRepo;
        this.resumeRepo = resumeRepo;
        this.atsService = atsService;
    }

    // ── Run ATS for a job (score + persist) ──────────────────────────────

    @Transactional
    public JobAtsResponseDto runAtsForJob(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        List<Application> applications = appRepo.findByJobId(jobId);

        if (applications.isEmpty()) {
            return JobAtsResponseDto.empty(job.getTitle(), jobId);
        }

        // Build a userId → Resume map for fast lookup
        Map<Long, Resume> resumeMap = buildResumeMap(applications);

        List<JobAtsResponseDto.CandidateAtsResult> results = new ArrayList<>();
        int shortlisted = 0;
        int rejected    = 0;
        int skipped     = 0;

        for (Application app : applications) {
            Long userId = app.getApplicant().getId();
            Resume resume = resumeMap.get(userId);

            if (resume == null || resume.getResumeText() == null || resume.getResumeText().isBlank()) {
                // No resume — mark rejected
                app.setAtsScore(0);
                app.setAtsStatus("REJECTED");
                app.setAtsCheckedAt(LocalDateTime.now());
                app.setShortlistReason("No resume uploaded.");
                appRepo.save(app);
                skipped++;

                results.add(JobAtsResponseDto.CandidateAtsResult.builder()
                        .applicationId(app.getId())
                        .candidateName(app.getApplicant().getName())
                        .candidateEmail(app.getApplicant().getEmail())
                        .atsScore(0)
                        .atsStatus("REJECTED")
                        .matchedSkills(List.of())
                        .missingSkills(List.of())
                        .note("No resume found")
                        .build());
                continue;
            }

            // Score against THIS job's requirements
            AtsResultDto score = atsService.checkForJob(resume.getResumeText(), job);

            String status = score.getAtsScore() >= AtsService.ATS_THRESHOLD ? "SHORTLISTED" : "REJECTED";

            // Persist
            app.setAtsScore(score.getAtsScore());
            app.setAtsStatus(status);
            app.setAtsCheckedAt(LocalDateTime.now());
            app.setShortlistReason(buildReason(score, job));
            app.setShortlistSource("ATS");
            if ("SHORTLISTED".equals(status)) {
                app.setShortlistedAt(LocalDateTime.now());
                // FIX: sync status enum so BulkInterviewAssignService and AIInterviewService
                // can find this application when assigning interview sessions.
                app.setStatus(ApplicationStatus.SHORTLISTED);
                shortlisted++;
            } else {
                rejected++;
            }
            appRepo.save(app);

            results.add(JobAtsResponseDto.CandidateAtsResult.builder()
                    .applicationId(app.getId())
                    .candidateName(app.getApplicant().getName())
                    .candidateEmail(app.getApplicant().getEmail())
                    .atsScore(score.getAtsScore())
                    .atsStatus(status)
                    .matchedSkills(score.getMatchedKeywords())
                    .missingSkills(score.getMissingKeywords())
                    .note(score.getShortlistMessage())
                    .build());
        }

        // Sort: shortlisted first, then by score desc
        results.sort(Comparator
                .comparing((JobAtsResponseDto.CandidateAtsResult r) -> "SHORTLISTED".equals(r.getAtsStatus()) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(JobAtsResponseDto.CandidateAtsResult::getAtsScore).reversed()));

        return JobAtsResponseDto.builder()
                .jobId(jobId)
                .jobTitle(job.getTitle())
                .threshold(AtsService.ATS_THRESHOLD)
                .totalProcessed(applications.size())
                .shortlisted(shortlisted)
                .rejected(rejected)
                .skipped(skipped)
                .candidates(results)
                .message(String.format("ATS complete for '%s'. Shortlisted: %d | Rejected: %d | Skipped: %d",
                        job.getTitle(), shortlisted, rejected, skipped))
                .build();
    }

    // ── Get shortlisted candidates for a job ─────────────────────────────

    public JobAtsResponseDto getShortlisted(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        List<Application> apps = appRepo.findShortlistedByJobId(jobId);

        List<JobAtsResponseDto.CandidateAtsResult> results = apps.stream()
                .map(app -> JobAtsResponseDto.CandidateAtsResult.builder()
                        .applicationId(app.getId())
                        .candidateName(app.getApplicant().getName())
                        .candidateEmail(app.getApplicant().getEmail())
                        .atsScore(app.getAtsScore() != null ? app.getAtsScore() : 0)
                        .atsStatus(app.getAtsStatus())
                        .matchedSkills(List.of())
                        .missingSkills(List.of())
                        .build())
                .collect(Collectors.toList());

        long shortlisted = appRepo.countShortlistedByJobId(jobId);
        long rejected    = appRepo.countRejectedByJobId(jobId);

        return JobAtsResponseDto.builder()
                .jobId(jobId)
                .jobTitle(job.getTitle())
                .threshold(AtsService.ATS_THRESHOLD)
                .totalProcessed((int)(shortlisted + rejected))
                .shortlisted((int) shortlisted)
                .rejected((int) rejected)
                .skipped(0)
                .candidates(results)
                .message("Shortlisted candidates for job: " + job.getTitle())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Map<Long, Resume> buildResumeMap(List<Application> applications) {
        Set<Long> userIds = applications.stream()
                .map(a -> a.getApplicant().getId())
                .collect(Collectors.toSet());

        Map<Long, Resume> map = new HashMap<>();
        // Find most-recent resume per user
        resumeRepo.findAllWithUser().stream()
                .filter(r -> userIds.contains(r.getUser().getId()))
                .forEach(r -> map.merge(r.getUser().getId(), r,
                        (a, b) -> a.getUploadedAt().isAfter(b.getUploadedAt()) ? a : b));
        return map;
    }

    private String buildReason(AtsResultDto score, Job job) {
        int matched = score.getMatchedKeywords() != null ? score.getMatchedKeywords().size() : 0;
        int total   = matched + (score.getMissingKeywords() != null ? score.getMissingKeywords().size() : 0);
        String kws  = score.getMatchedKeywords() != null ? String.join(", ", score.getMatchedKeywords()) : "";
        return String.format("ATS score %d/100 (threshold %d) for '%s'. Matched %d/%d skills: %s.",
                score.getAtsScore(), AtsService.ATS_THRESHOLD, job.getTitle(), matched, total, kws);
    }
}