package com.hirex.controller;

import com.hirex.dto.JobDto.JobRequest;
import com.hirex.dto.JobDto.JobResponse;
import com.hirex.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    // public browse - no auth needed
    @GetMapping("/jobs/browse")
    public ResponseEntity<List<JobResponse>> browse(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return ResponseEntity.ok(jobService.searchJobs(keyword));
        }
        return ResponseEntity.ok(jobService.getAllActiveJobs());
    }
    // FILE: src/main/java/com/hirex/controller/JobController.java

    // ADD this new endpoint (keep existing /api/jobs/browse unchanged for candidates)
    @GetMapping("/manager/jobs/search")
    public ResponseEntity<List<JobResponse>> searchMyJobs(
            @RequestParam(required = false) String keyword,
            Principal principal) {
        if (keyword != null && !keyword.isBlank()) {
            return ResponseEntity.ok(jobService.searchManagerJobs(keyword, principal.getName()));
        }
        return ResponseEntity.ok(jobService.getManagerJobs(principal.getName()));
    }

    @PostMapping("/manager/jobs")
    public ResponseEntity<JobResponse> createJob(@RequestBody JobRequest req, Principal principal) {
        return ResponseEntity.ok(jobService.createJob(req, principal.getName()));
    }

    @PutMapping("/manager/jobs/{id}")
    public ResponseEntity<JobResponse> updateJob(@PathVariable Long id,
                                                  @RequestBody JobRequest req,
                                                  Principal principal) {
        return ResponseEntity.ok(jobService.updateJob(id, req, principal.getName()));
    }

    @DeleteMapping("/manager/jobs/{id}")
    public ResponseEntity<String> deleteJob(@PathVariable Long id, Principal principal) {
        jobService.deleteJob(id, principal.getName());
        return ResponseEntity.ok("Job removed");
    }

    @GetMapping("/manager/jobs")
    public ResponseEntity<List<JobResponse>> myJobs(Principal principal) {
        return ResponseEntity.ok(jobService.getManagerJobs(principal.getName()));
    }
}
