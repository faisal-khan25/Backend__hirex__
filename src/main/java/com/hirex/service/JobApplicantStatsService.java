package com.hirex.service;



import com.hirex.dto.JobApplicantStatsDto;
import com.hirex.entity.ApplicationStatus;
import com.hirex.entity.Job;
import com.hirex.repository.ApplicationRepository;
import com.hirex.repository.InterviewSessionRepository;
import com.hirex.repository.JobRepository;
import org.springframework.stereotype.Service;

/**
 * JobApplicantStatsService
 *
 * Computes all applicant-related statistics SCOPED TO A SINGLE JOB.
 *
 * This is the single source of truth for header/dashboard stats on the
 * ATS Analysis page.  Every count is derived from the job's own
 * applications and interview sessions — never from the whole database.
 *
 * Endpoint consumed by: GET /api/jobs/{jobId}/stats
 */
@Service
public class JobApplicantStatsService {

    private final JobRepository              jobRepo;
    private final ApplicationRepository      appRepo;
    private final InterviewSessionRepository sessionRepo;

    public JobApplicantStatsService(JobRepository jobRepo,
                                    ApplicationRepository appRepo,
                                    InterviewSessionRepository sessionRepo) {
        this.jobRepo     = jobRepo;
        this.appRepo     = appRepo;
        this.sessionRepo = sessionRepo;
    }

    /**
     * Return all applicant statistics for the given job.
     *
     * @param jobId the job whose stats are requested
     * @return a fully-populated {@link JobApplicantStatsDto}
     * @throws RuntimeException if the job is not found
     */
    public JobApplicantStatsDto getStatsForJob(Long jobId) {
        Job job = jobRepo.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        long total            = appRepo.countByJobId(jobId);
        long hired            = appRepo.countByJobIdAndStatus(jobId, ApplicationStatus.HIRED);
        long shortlisted      = appRepo.countByJobIdAndStatus(jobId, ApplicationStatus.SHORTLISTED);
        long rejected         = appRepo.countByJobIdAndStatus(jobId, ApplicationStatus.REJECTED);
        long pending          = appRepo.countByJobIdAndStatus(jobId, ApplicationStatus.APPLIED);
        long atsShortlisted   = appRepo.countByJobIdAndAtsStatus(jobId, "SHORTLISTED");
        long atsRejected      = appRepo.countByJobIdAndAtsStatus(jobId, "REJECTED");
        long interviewAssigned  = sessionRepo.countByJobId(jobId);
        long interviewCompleted = sessionRepo.countCompletedByJobId(jobId); // ★ Now includes all finish states

        // ★ NEW: granular pass/fail counts
        long interviewPassed      = sessionRepo.countPassedByJobId(jobId);
        long interviewFailed      = sessionRepo.countFailedByJobId(jobId);
        long interviewUnderReview = sessionRepo.countUnderReviewByJobId(jobId);

        return JobApplicantStatsDto.builder()
                .jobId(jobId)
                .jobTitle(job.getTitle())
                .totalApplicants(total)
                .hired(hired)
                .shortlisted(shortlisted)
                .rejected(rejected)
                .pending(pending)
                .interviewAssigned(interviewAssigned)
                .interviewCompleted(interviewCompleted)
                .interviewPassed(interviewPassed)         // ★ NEW
                .interviewFailed(interviewFailed)         // ★ NEW
                .interviewUnderReview(interviewUnderReview) // ★ NEW
                .atsShortlisted(atsShortlisted)
                .atsRejected(atsRejected)
                .build();
    }
}
