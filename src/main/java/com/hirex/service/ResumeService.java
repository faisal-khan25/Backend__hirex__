package com.hirex.service;

import com.hirex.dto.ResumeDto;
import com.hirex.entity.Application;
import com.hirex.entity.Resume;
import com.hirex.entity.User;
import com.hirex.repository.ApplicationRepository;
import com.hirex.repository.ResumeRepository;
import com.hirex.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;

    @Value("${resume.upload.dir:uploads/resumes}")
    private String uploadDir;

    public ResumeService(ResumeRepository resumeRepository,
                         UserRepository userRepository,
                         ApplicationRepository applicationRepository) {
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "txt";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    // ─────────────────────── UPLOAD ────────────────────────────────────

    public Resume uploadResume(MultipartFile file, String userEmail) throws IOException {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Path dirPath = Paths.get(uploadDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        String savedName = UUID.randomUUID() + "." + extension;
        Path targetPath = dirPath.resolve(savedName);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String resumeText = extractText(file, extension);

        Resume resume = resumeRepository.findByUser(user).orElse(new Resume());
        resume.setUser(user);
        resume.setFileName(originalName);
        resume.setFilePath(targetPath.toString());
        resume.setResumeText(resumeText);

        return resumeRepository.save(resume);
    }

    public byte[] downloadFile(Long resumeId) throws IOException {
        Resume resume = getById(resumeId);
        Path path = Paths.get(resume.getFilePath());
        if (!Files.exists(path)) {
            throw new RuntimeException("File not found: " + resume.getFilePath());
        }
        return Files.readAllBytes(path);
    }

    // ─────────────────────── QUERY ─────────────────────────────────────

    public Resume getByUserId(Long userId) {
        return resumeRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Resume not found for user " + userId));
    }

    public Resume getById(Long resumeId) {
        return resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found: " + resumeId));
    }

    public Resume getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return resumeRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("No resume uploaded yet"));
    }

    /**
     * All resumes for the manager dashboard.
     * Now also populates applicationId so the ATS checker can auto-update
     * the application status without requiring a separate lookup.
     */
    public List<ResumeDto> getAllResumes() {
        return resumeRepository.findAllWithUser().stream()
                .map(r -> {
                    ResumeDto dto = new ResumeDto();
                    dto.setResumeId(r.getId());
                    dto.setUserId(r.getUser().getId());
                    dto.setCandidateName(r.getUser().getName());
                    dto.setCandidateEmail(r.getUser().getEmail());
                    dto.setFileName(r.getFileName());
                    dto.setFilePath(r.getFilePath());
                    dto.setUploadedAt(r.getUploadedAt());
                    dto.setHasText(r.getResumeText() != null && !r.getResumeText().isBlank());

                    // Attach applicationId — pick the most recent application by this candidate
                    List<Application> apps = applicationRepository.findByApplicant(r.getUser());
                    if (!apps.isEmpty()) {
                        Application latest = apps.stream()
                                .max(java.util.Comparator.comparing(
                                        a -> a.getAppliedAt() != null ? a.getAppliedAt()
                                                : java.time.LocalDateTime.MIN))
                                .orElse(apps.get(0));
                        dto.setApplicationId(latest.getId());
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    // ─────────────────────── TEXT EXTRACTION ───────────────────────────

    private String extractText(MultipartFile file, String extension) {
        try {
            return switch (extension.toLowerCase()) {
                case "pdf"  -> extractFromPdf(file);
                case "docx" -> extractFromDocx(file);
                default     -> new String(file.getBytes());
            };
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String extractFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractFromDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
            return text.toString();
        }
    }
}
