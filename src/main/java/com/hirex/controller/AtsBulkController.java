package com.hirex.controller;

import org.springframework.web.bind.annotation.*;
import com.hirex.service.AtsBulkService;
import com.hirex.dto.AtsBulkResponseDto;
import com.hirex.dto.AtsSummaryDto;

import org.springframework.http.ResponseEntity;

import java.security.Principal;

/**
 * FIX: Removed duplicate @Autowired field injection that conflicted with
 * the constructor injection, causing a NullPointerException on startup
 * in some Spring Boot versions (field gets injected first, then overwritten
 * by constructor — but with @Autowired on the field the container can
 * inject null during the constructor call window).
 */
@RestController
@CrossOrigin
public class AtsBulkController {

    // FIX: removed @Autowired field — constructor injection is the only injection path
    private final AtsBulkService atsBulkService;

    public AtsBulkController(AtsBulkService atsBulkService) {
        this.atsBulkService = atsBulkService;
    }

    // ── Global endpoints (legacy — operate on ALL recruiters' data) ───────

    @PostMapping("/api/ats/analyze-all")
    public ResponseEntity<AtsBulkResponseDto> analyzeAll() {
        AtsBulkResponseDto response = atsBulkService.analyzeAll();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/ats/process-all")
    public ResponseEntity<AtsBulkResponseDto> processAll() {
        AtsBulkResponseDto response = atsBulkService.processAll();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/ats/summary")
    public ResponseEntity<AtsSummaryDto> summary() {
        AtsSummaryDto dto = atsBulkService.getSummary();
        return ResponseEntity.ok(dto);
    }

    // ── Recruiter-scoped endpoints (NEW) — scoped to the logged-in manager ─
    //
    // These are what the frontend AtsChecker calls so each recruiter only
    // sees counts and processes applications belonging to their own jobs.
    // All three use Principal (the JWT-authenticated user's email) to filter.

    /**
     * GET /api/manager/ats/summary
     *
     * Returns dashboard stats (Total Applicants, Hired, Shortlisted,
     * Rejected, Pending, Resumes Uploaded) scoped ONLY to the currently
     * logged-in recruiter's jobs.  Replaces /api/ats/summary for the
     * ATS Analysis page header.
     */
    @GetMapping("/api/manager/ats/summary")
    public ResponseEntity<AtsSummaryDto> managerSummary(Principal principal) {
        AtsSummaryDto dto = atsBulkService.getSummaryForManager(principal.getName());
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/manager/ats/analyze-all
     *
     * Scores (preview-only, no DB write) all resumes belonging to
     * applicants who applied to this recruiter's jobs.
     */
    @PostMapping("/api/manager/ats/analyze-all")
    public ResponseEntity<AtsBulkResponseDto> managerAnalyzeAll(Principal principal) {
        AtsBulkResponseDto response = atsBulkService.analyzeAllForManager(principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/manager/ats/process-all
     *
     * Scores all applicable resumes AND persists the derived status
     * (SHORTLISTED / REJECTED) to the DB — but only for applications
     * belonging to this recruiter's jobs.
     */
    @PostMapping("/api/manager/ats/process-all")
    public ResponseEntity<AtsBulkResponseDto> managerProcessAll(Principal principal) {
        AtsBulkResponseDto response = atsBulkService.processAllForManager(principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/manager/ats/jobs/{jobId}/analyze
     *
     * Scores (preview-only, no DB write) resumes for applicants of a
     * specific job, verifying the job belongs to the requesting recruiter.
     */
    @PostMapping("/api/manager/ats/jobs/{jobId}/analyze")
    public ResponseEntity<AtsBulkResponseDto> analyzeJob(@PathVariable Long jobId,
                                                         Principal principal) {
        AtsBulkResponseDto response = atsBulkService.analyzeForJob(jobId, principal.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/manager/ats/jobs/{jobId}/process
     *
     * Scores all resumes for a specific job AND persists derived statuses
     * (SHORTLISTED / REJECTED) — only if the job belongs to this recruiter.
     */
    @PostMapping("/api/manager/ats/jobs/{jobId}/process")
    public ResponseEntity<AtsBulkResponseDto> processJob(@PathVariable Long jobId,
                                                         Principal principal) {
        AtsBulkResponseDto response = atsBulkService.processForJob(jobId, principal.getName());
        return ResponseEntity.ok(response);
    }
}