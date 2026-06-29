package com.hirex.repository;

import com.hirex.entity.Resume;
import com.hirex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.hirex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByUser(User user);

    Optional<Resume> findByUserId(Long userId);

    // Fetch all resumes with their associated user (for manager view)
    @Query("SELECT r FROM Resume r JOIN FETCH r.user ORDER BY r.uploadedAt DESC")
    List<Resume> findAllWithUser();

    /** Resumes belonging to applicants who applied to any of this manager's jobs. */
    @Query("""
            SELECT DISTINCT r
            FROM Resume r
            JOIN FETCH r.user u
            WHERE EXISTS (
                SELECT 1 FROM Application a
                WHERE a.applicant = u
                AND a.job.company.manager.email = :managerEmail
            )
            ORDER BY r.uploadedAt DESC
            """)
    List<Resume> findByManagerEmail(@Param("managerEmail") String managerEmail);
}