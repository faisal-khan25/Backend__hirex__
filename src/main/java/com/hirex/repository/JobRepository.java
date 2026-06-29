package com.hirex.repository;

import com.hirex.entity.Company;
import com.hirex.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByActiveTrue();
    List<Job> findByCompany(Company company);
    List<Job> findByTitleContainingIgnoreCaseAndActiveTrue(String keyword);
    List<Job> findByCompanyAndTitleContainingIgnoreCase(Company company, String keyword);

    /**
     * FIX: Find a job only if it belongs to the given manager (via company).
     * Used in ApplicationService.getApplicantsForJob() to enforce ownership
     * before returning any applicant data.
     */
    @Query("""
            SELECT j FROM Job j
            JOIN j.company c
            WHERE j.id = :jobId
            AND c.manager.email = :managerEmail
            """)
    Optional<Job> findByIdAndManagerEmail(@Param("jobId") Long jobId,
                                          @Param("managerEmail") String managerEmail);
}