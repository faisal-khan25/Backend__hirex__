package com.hirex.controller;

import com.hirex.service.ApplicationService;
import com.hirex.service.ApplicationService.AppResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApplicationController {

    private final ApplicationService appService;

    public ApplicationController(ApplicationService appService) {
        this.appService = appService;
    }

    @PostMapping("/jobseeker/apply/{jobId}")
    public ResponseEntity<String> apply(@PathVariable Long jobId,
                                        @RequestBody ApplyRequest req,
                                        Principal principal) {
        return ResponseEntity.ok(appService.apply(jobId, req.getCoverLetter(), principal.getName()));
    }
    /**
     * Returns the current interview status for a specific application.
     * Called by the candidate dashboard to show interview progress.
     */
    @GetMapping("/jobseeker/applications/{appId}/interview-status")
    public ResponseEntity<?> getInterviewStatus(@PathVariable Long appId, Principal principal) {
        return ResponseEntity.ok(appService.getInterviewStatusForApplication(appId, principal.getName()));
    }

    @GetMapping("/jobseeker/applications")
    public ResponseEntity<List<AppResponse>> myApplications(Principal principal) {
        return ResponseEntity.ok(appService.getMyApplications(principal.getName()));
    }

    @GetMapping("/manager/jobs/{jobId}/applicants")
    public ResponseEntity<List<AppResponse>> applicants(@PathVariable Long jobId, Principal principal) {
        return ResponseEntity.ok(appService.getApplicantsForJob(jobId, principal.getName()));
    }

    @GetMapping("/manager/shortlisted-applicants")
    public ResponseEntity<List<AppResponse>> shortlistedApplicants(Principal principal) {
        return ResponseEntity.ok(appService.getShortlistedApplicantsForManager(principal.getName()));
    }

    /**
     * Manual status update (existing — kept as fallback per requirement 5).
     */
    @PutMapping("/manager/applications/{appId}/status")
    public ResponseEntity<AppResponse> updateStatus(@PathVariable Long appId,
                                                    @RequestBody StatusRequest req,
                                                    Principal principal) {
        return ResponseEntity.ok(appService.updateStatus(appId, req.getStatus(), principal.getName()));
    }

    /**
     * ATS-driven shortlisting endpoint.
     * Called by the AtsChecker frontend after a successful ATS score to
     * automatically shortlist a candidate and persist the score + reason.
     * Body: { "atsScore": 75, "shortlistReason": "..." }
     */
    @PutMapping("/manager/applications/{appId}/ats-shortlist")
    public ResponseEntity<AppResponse> atsShortlist(@PathVariable Long appId,
                                                    @RequestBody AtsShortlistRequest req,
                                                    Principal principal) {
        return ResponseEntity.ok(
                appService.atsShortlist(appId, req.getAtsScore(), req.getShortlistReason()));
    }

    // ── Inner request classes ──────────────────────────────────────────

    public static class ApplyRequest {
        private String coverLetter;
        public String getCoverLetter() { return coverLetter; }
        public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }
    }

    public static class StatusRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class AtsShortlistRequest {
        private int atsScore;
        private String shortlistReason;
        public int getAtsScore() { return atsScore; }
        public void setAtsScore(int atsScore) { this.atsScore = atsScore; }
        public String getShortlistReason() { return shortlistReason; }
        public void setShortlistReason(String shortlistReason) { this.shortlistReason = shortlistReason; }
    }
}
