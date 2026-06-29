package com.hirex.repository;

import com.hirex.entity.Application;
import com.hirex.entity.ApplicationStatus;
import com.hirex.entity.Job;
import com.hirex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // Basic methods
    List<Application> findByApplicant(User user);

    List<Application> findByJob(Job job);

    List<Application> findByJobIn(List<Job> jobs);

    boolean existsByJobAndApplicant(Job job, User user);

    Optional<Application> findByJobAndApplicant(Job job, User user);

    // Admin dashboard – count applications per company
    @Query("""
            SELECT a.job.company.name, COUNT(a)
            FROM Application a
            GROUP BY a.job.company.name
            """)
    List<Object[]> countApplicationsPerCompany();

    // Count all applications by status
    long countByStatus(ApplicationStatus status);

    // Count total applications for a company
    @Query("""
            SELECT COUNT(a)
            FROM Application a
            WHERE a.job.company.id = :companyId
            """)
    long countByCompanyId(@Param("companyId") Long companyId);

    // Count applications by company and status
    @Query("""
            SELECT COUNT(a)
            FROM Application a
            WHERE a.job.company.id = :companyId
            AND a.status = :status
            """)
    long countByCompanyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") ApplicationStatus status
    );

    // Find all applications belonging to a company
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.applicant
            JOIN FETCH a.job
            WHERE a.job.company.id = :companyId
            """)
    List<Application> findByCompanyId(
            @Param("companyId") Long companyId
    );

    // Find applications by applicant id
    @Query("""
            SELECT a
            FROM Application a
            WHERE a.applicant.id = :userId
            ORDER BY a.appliedAt DESC
            """)
    List<Application> findByApplicantId(
            @Param("userId") Long userId
    );

    // Bulk update application status
    @Modifying
    @Query("""
            UPDATE Application a
            SET a.status = :status
            WHERE a.applicant.id = :userId
            AND a.job.id = :jobId
            """)
    int updateStatusByUserAndJob(
            @Param("userId") Long userId,
            @Param("jobId") Long jobId,
            @Param("status") ApplicationStatus status
    );

    // Get all shortlisted applications for a manager
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.applicant
            JOIN FETCH a.job j
            JOIN FETCH j.company c
            WHERE c.manager = :manager
            AND a.status = :status
            ORDER BY a.appliedAt DESC
            """)
    List<Application> findShortlistedApplicationsByManager(
            @Param("manager") User manager,
            @Param("status") ApplicationStatus status
    );

    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.applicant
            JOIN FETCH a.job j
            JOIN FETCH j.company c
            WHERE c.manager = :manager
            AND a.status IN :statuses
            ORDER BY a.appliedAt DESC
            """)
    List<Application> findVisibleChatApplicationsByManager(
            @Param("manager") User manager,
            @Param("statuses") List<ApplicationStatus> statuses
    );
//    @Query("SELECT a FROM Application a " +
//            "WHERE a.job.company.manager = :manager " +
//            "AND a.status IN (:statuses) " +
//            "ORDER BY a.appliedAt DESC")
//    List<Application> findShortlistedApplicationsByManager(
//            @Param("manager") User manager,
//            @Param("statuses") List<ApplicationStatus> statuses
//    );


    // Find applications of a candidate under a specific manager
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.job j
            JOIN FETCH j.company c
            WHERE a.applicant = :candidate
            AND c.manager = :manager
            ORDER BY a.appliedAt DESC
            """)
    List<Application> findApplicationsForCandidateUnderManager(
            @Param("candidate") User candidate,
            @Param("manager") User manager
    );

    // ── Job-specific ATS queries ────────────────────────────────────────

    /** All applications for a specific job (with applicant eager-loaded). */
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.applicant
            JOIN FETCH a.job j
            WHERE j.id = :jobId
            ORDER BY a.appliedAt DESC
            """)
    List<Application> findByJobId(@Param("jobId") Long jobId);

    /** Shortlisted applications for a specific job (ats_status = 'SHORTLISTED'). */
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.applicant
            JOIN FETCH a.job j
            WHERE j.id = :jobId
            AND a.atsStatus = 'SHORTLISTED'
            ORDER BY COALESCE(a.atsScore, 0) DESC
            """)
    List<Application> findShortlistedByJobId(@Param("jobId") Long jobId);

    /** Count shortlisted for a job. */
    @Query("SELECT COUNT(a) FROM Application a WHERE a.job.id = :jobId AND a.atsStatus = 'SHORTLISTED'")
    long countShortlistedByJobId(@Param("jobId") Long jobId);

    /** Count rejected for a job. */
    @Query("SELECT COUNT(a) FROM Application a WHERE a.job.id = :jobId AND a.atsStatus = 'REJECTED'")
    long countRejectedByJobId(@Param("jobId") Long jobId);


    // ── Per-job status counts (used by JobApplicantStatsDto) ────────────

    /** Total applications for a specific job. */
    @Query("SELECT COUNT(a) FROM Application a WHERE a.job.id = :jobId")
    long countByJobId(@Param("jobId") Long jobId);

    /** Applications for a job with a given ApplicationStatus. */
    @Query("SELECT COUNT(a) FROM Application a WHERE a.job.id = :jobId AND a.status = :status")
    long countByJobIdAndStatus(@Param("jobId") Long jobId,
                               @Param("status") ApplicationStatus status);

    /** Applications for a job with a given atsStatus string (e.g. 'SHORTLISTED'). */
    @Query("SELECT COUNT(a) FROM Application a WHERE a.job.id = :jobId AND a.atsStatus = :atsStatus")
    long countByJobIdAndAtsStatus(@Param("jobId") Long jobId,
                                  @Param("atsStatus") String atsStatus);
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.applicant
            JOIN FETCH a.job j
            WHERE j.id = :jobId
            AND (a.status = com.hirex.entity.ApplicationStatus.SHORTLISTED OR a.atsStatus = 'SHORTLISTED')
            AND NOT EXISTS (
                SELECT 1 FROM InterviewSession s WHERE s.application = a
            )
            """)
    List<Application> findShortlistedWithoutSession(@Param("jobId") Long jobId);

    // ── Recruiter-scoped counts (scoped to jobs owned by this manager) ────

    /** Total applications across ALL jobs owned by this manager. */
    @Query("""
            SELECT COUNT(a)
            FROM Application a
            WHERE a.job.company.manager.email = :managerEmail
            """)
    long countByManagerEmail(@Param("managerEmail") String managerEmail);

    /** Applications with a given status across ALL jobs owned by this manager. */
    @Query("""
            SELECT COUNT(a)
            FROM Application a
            WHERE a.job.company.manager.email = :managerEmail
            AND a.status = :status
            """)
    long countByManagerEmailAndStatus(@Param("managerEmail") String managerEmail,
                                      @Param("status") ApplicationStatus status);

    /** Distinct applicants with a resume, across this manager's jobs. */
    @Query("""
            SELECT COUNT(DISTINCT a.applicant.id)
            FROM Application a
            WHERE a.job.company.manager.email = :managerEmail
            AND EXISTS (
                SELECT 1 FROM Resume r WHERE r.user = a.applicant
            )
            """)
    long countApplicantsWithResumeByManager(@Param("managerEmail") String managerEmail);

    /** All applications for jobs owned by this manager (for bulk ATS). */
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.applicant
            JOIN FETCH a.job j
            JOIN FETCH j.company c
            WHERE c.manager.email = :managerEmail
            ORDER BY a.appliedAt DESC
            """)
    List<Application> findByManagerEmail(@Param("managerEmail") String managerEmail);

}