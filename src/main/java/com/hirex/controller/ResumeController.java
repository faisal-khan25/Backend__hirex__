package com.hirex.controller;



import com.hirex.dto.AtsResultDto;
import com.hirex.dto.ResumeDto;
import com.hirex.entity.Resume;
import com.hirex.service.AtsService;
import com.hirex.service.ResumeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
public class ResumeController {

    private final ResumeService resumeService;
    private final AtsService    atsService;

    public ResumeController(ResumeService resumeService, AtsService atsService) {
        this.resumeService = resumeService;
        this.atsService    = atsService;
    }

    // ──────────────────── JOBSEEKER ENDPOINTS ──────────────────────────

    /**
     * POST /api/resume/upload
     * Upload or replace the logged-in user's resume.
     * Accepts PDF or DOCX.
     */
    @PostMapping("/api/resume/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            Principal principal) throws IOException {

        String ct = file.getContentType();
        if (ct == null ||
                (!ct.equals("application/pdf") &&
                        !ct.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only PDF and DOCX files are supported."));
        }

        resumeService.uploadResume(file, principal.getName());
        return ResponseEntity.ok(Map.of("message", "Resume uploaded successfully."));
    }

    /**
     * GET /api/resume/my
     * Get the logged-in user's resume metadata.
     */
//    @GetMapping("/api/resume/my")
//    public ResponseEntity<Resume> myResume(Principal principal) {
//        try {
//            Resume r = resumeService.getByEmail(principal.getName());
//            // Don't expose full text in listing response
//            r.setResumeText(null);
//            return ResponseEntity.ok(r);
//        } catch (RuntimeException e) {
//            return ResponseEntity.notFound().build();
//        }
    @GetMapping("/api/resume/my")
    public ResponseEntity<Resume> myResume(Principal principal) {
        try {
            Resume r = resumeService.getByEmail(principal.getName());
            r.setResumeText(null);
            return ResponseEntity.ok(r);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // return 404, not 500
        }
    }



    // ──────────────────── MANAGER ENDPOINTS ────────────────────────────

    /**
     * GET /api/manager/resumes
     * List all candidates and their uploaded resumes.
     */
    @GetMapping("/api/manager/resumes")
    public ResponseEntity<List<ResumeDto>> allResumes() {
        return ResponseEntity.ok(resumeService.getAllResumes());
    }

    /**
     * GET /api/manager/resume/{id}
     * Get a single resume's metadata.
     */
    @GetMapping("/api/manager/resume/{id}")
    public ResponseEntity<ResumeDto> getResume(@PathVariable Long id) {
        Resume r = resumeService.getById(id);
        ResumeDto dto = new ResumeDto();
        dto.setResumeId(r.getId());
        dto.setUserId(r.getUser().getId());
        dto.setCandidateName(r.getUser().getName());
        dto.setCandidateEmail(r.getUser().getEmail());
        dto.setFileName(r.getFileName());
        dto.setFilePath(r.getFilePath());
        dto.setUploadedAt(r.getUploadedAt());
        dto.setHasText(r.getResumeText() != null && !r.getResumeText().isBlank());
        return ResponseEntity.ok(dto);
    }

    /**
     * GET /api/manager/resume/{id}/ats-score
     * Run ATS analysis on a stored resume.
     */
    @GetMapping("/api/manager/resume/{id}/ats-score")
    public ResponseEntity<AtsResultDto> atsScore(@PathVariable Long id) {
        Resume resume = resumeService.getById(id);
        AtsResultDto result = atsService.check(resume.getResumeText());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/manager/resume/{id}/download
     * Download the actual resume file.
     */
    @GetMapping("/api/manager/resume/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws IOException {
        Resume resume = resumeService.getById(id);
        byte[] data   = resumeService.downloadFile(id);

        String fn = resume.getFileName() != null ? resume.getFileName() : "resume";
        String ct = fn.endsWith(".pdf")
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fn + "\"")
                .contentType(MediaType.parseMediaType(ct))
                .body(data);
    }
}
