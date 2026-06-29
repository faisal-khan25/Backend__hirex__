package com.hirex.controller;

import com.hirex.dto.BulkInterviewAssignResponseDto;
import com.hirex.dto.JobApplicantStatsDto;
import com.hirex.dto.JobAtsResponseDto;
import com.hirex.service.BulkInterviewAssignService;
import com.hirex.service.JobApplicantStatsService;
import com.hirex.service.JobAtsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * JobAtsController
 *
 * Endpoints:
 *
 *   POST /api/jobs/{jobId}/ats/run
 *       Run job-specific ATS scoring for all applicants of this job.
 *       Persists atsScore + atsStatus to the database.
 *
 *   GET  /api/jobs/{jobId}/shortlisted
 *       Returns all shortlisted candidates for a job (atsStatus = SHORTLISTED).
 *
 *   POST /api/interview/assign-all/{jobId}
 *       Assign AI interview to every shortlisted applicant for the job.
 *       Duplicate-safe: skips applicants who already have an interview.
 *
 *   POST /api/interview/assign/{applicationId}
 *       Assign AI interview to a single applicant.
 */
@RestController
@CrossOrigin
public class JobAtsController {

    private final JobAtsService              jobAtsService;
    private final BulkInterviewAssignService bulkService;
    private final JobApplicantStatsService   statsService;

    public JobAtsController(JobAtsService jobAtsService,
                            BulkInterviewAssignService bulkService,
                            JobApplicantStatsService statsService) {
        this.jobAtsService = jobAtsService;
        this.bulkService   = bulkService;
        this.statsService  = statsService;
    }

    // ── ATS endpoints ─────────────────────────────────────────────────────

    /**
     * POST /api/jobs/{jobId}/ats/run
     *
     * Runs job-specific ATS on all applicants for the given job.
     * Scores each resume against this job's skills/experience/education.
     * Persists atsScore + atsStatus (SHORTLISTED / REJECTED) to the DB.
     *
     * Threshold: 80+ → SHORTLISTED, below 80 → REJECTED
     */
    @PostMapping("/api/jobs/{jobId}/ats/run")
    public ResponseEntity<JobAtsResponseDto> runJobAts(@PathVariable Long jobId) {
        JobAtsResponseDto response = jobAtsService.runAtsForJob(jobId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/jobs/{jobId}/shortlisted
     *
     * Returns all candidates shortlisted by ATS for a specific job.
     * Includes ATS score and status badge info for the frontend.
     */
    @GetMapping("/api/jobs/{jobId}/shortlisted")
    public ResponseEntity<JobAtsResponseDto> getShortlisted(@PathVariable Long jobId) {
        JobAtsResponseDto response = jobAtsService.getShortlisted(jobId);
        return ResponseEntity.ok(response);
    }

    // ── Per-job applicant statistics endpoint ────────────────────────────

    /**
     * GET /api/jobs/{jobId}/stats
     *
     * Returns all applicant-related statistics SCOPED TO THE SELECTED JOB.
     * Used by the ATS Analysis page header to display accurate counts.
     *
     * Response fields:
     *   jobId, jobTitle,
     *   totalApplicants, hired, shortlisted, rejected, pending,
     *   interviewAssigned, interviewCompleted,
     *   atsShortlisted, atsRejected
     *
     * All values are calculated only from applicants of the given job —
     * never from the whole database.
     */
    @GetMapping("/api/jobs/{jobId}/stats")
    public ResponseEntity<JobApplicantStatsDto> getJobStats(@PathVariable Long jobId) {
        return ResponseEntity.ok(statsService.getStatsForJob(jobId));
    }

    // ── Interview assignment endpoints ────────────────────────────────────

    /**
     * POST /api/interview/assign-all/{jobId}
     *
     * Assigns an AI interview to every shortlisted applicant for the job.
     *
     * - Finds all applications WHERE job_id = {jobId} AND atsStatus = 'SHORTLISTED'
     * - For each: creates InterviewSession (PENDING), generates interview link
     * - Skips any applicant who already has an interview (duplicate-safe)
     *
     * Response: { success, assigned, skipped, alreadyAssigned, message }
     */
    @PostMapping("/api/interview/assign-all/{jobId}")
    public ResponseEntity<BulkInterviewAssignResponseDto> assignAllInterviews(@PathVariable Long jobId) {
        BulkInterviewAssignResponseDto response = bulkService.assignAllForJob(jobId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/interview/assign/{applicationId}
     *
     * Assigns an AI interview to a single applicant.
     * Skips if an interview already exists (idempotent).
     */
//    @PostMapping("/api/interview/assign/{applicationId}")
    public ResponseEntity<BulkInterviewAssignResponseDto> assignInterview(@PathVariable Long applicationId) {
        BulkInterviewAssignResponseDto response = bulkService.assignToOne(applicationId);
        return ResponseEntity.ok(response);
    }
}
