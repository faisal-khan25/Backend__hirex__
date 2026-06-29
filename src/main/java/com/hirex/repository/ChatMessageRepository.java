package com.hirex.repository;

import com.hirex.entity.Application;
import com.hirex.entity.ChatMessage;
import com.hirex.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // OPTIMIZED: Pageable version for infinite-scroll / pagination.
    // Uses the composite index idx_chat_application_sent for O(log n) lookup.
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender " +
           "WHERE m.application = :application " +
           "ORDER BY m.sentAt ASC")
    Page<ChatMessage> findByApplicationPaged(
            @Param("application") Application application,
            Pageable pageable);

    // OPTIMIZED: Fetches messages WITH their reactions in a single JOIN query,
    // eliminating the N+1 problem that occurred with EAGER reactions before.
    // Used when loading a full conversation page.
    @Query("SELECT DISTINCT m FROM ChatMessage m " +
           "LEFT JOIN FETCH m.reactions r " +
           "LEFT JOIN FETCH r.user " +
           "JOIN FETCH m.sender " +
           "WHERE m.application = :application " +
           "ORDER BY m.sentAt ASC")
    List<ChatMessage> findByApplicationWithReactions(
            @Param("application") Application application);

    // OPTIMIZED: Pageable version of the above for large chat histories.
    // Returns IDs first to avoid LIMIT/OFFSET issues with JOIN FETCH.
    @Query("SELECT m.id FROM ChatMessage m WHERE m.application = :application ORDER BY m.sentAt ASC")
    Page<Long> findIdsByApplicationPaged(
            @Param("application") Application application,
            Pageable pageable);

    @Query("SELECT DISTINCT m FROM ChatMessage m " +
           "LEFT JOIN FETCH m.reactions r " +
           "LEFT JOIN FETCH r.user " +
           "JOIN FETCH m.sender " +
           "WHERE m.id IN :ids ORDER BY m.sentAt ASC")
    List<ChatMessage> findByIdsWithReactions(@Param("ids") List<Long> ids);

    // OPTIMIZED: Conversation summary — only fetch the last message per application,
    // avoiding the full message list load that caused slowness in getConversationsForManager.
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender " +
           "WHERE m.application = :application " +
           "AND m.sentAt = (SELECT MAX(m2.sentAt) FROM ChatMessage m2 WHERE m2.application = :application)")
    List<ChatMessage> findLastMessageForApplication(@Param("application") Application application);

    // OPTIMIZED: Batch last-message query for ALL conversations at once.
    // Replaces the per-conversation loop that caused N+1 queries in getConversationsForManager.
    @Query("SELECT m FROM ChatMessage m " +
           "JOIN FETCH m.sender " +
           "WHERE m.application IN :applications " +
           "AND m.sentAt = (" +
           "  SELECT MAX(m2.sentAt) FROM ChatMessage m2 WHERE m2.application = m.application" +
           ")")
    List<ChatMessage> findLastMessagesForApplications(
            @Param("applications") List<Application> applications);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.application = :application AND m.read = false AND m.sender != :currentUser")
    long countUnreadMessages(Application application, User currentUser);

    // OPTIMIZED: Batch unread counts for all applications in one query.
    @Query("SELECT m.application.id, COUNT(m) FROM ChatMessage m " +
           "WHERE m.application IN :applications AND m.read = false AND m.sender != :currentUser " +
           "GROUP BY m.application.id")
    List<Object[]> countUnreadMessagesForApplications(
            @Param("applications") List<Application> applications,
            @Param("currentUser") User currentUser);

    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.read = true WHERE m.application = :application AND m.sender != :currentUser")
    void markAllReadForApplication(Application application, User currentUser);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.application.applicant = :user AND m.read = false AND m.sender != :user")
    long countUnreadForJobseeker(User user);

    @Query("SELECT COUNT(DISTINCT m.application) FROM ChatMessage m WHERE m.application.job.company.manager = :manager AND m.read = false AND m.sender != :manager")
    long countConversationsWithUnreadForManager(User manager);

    @Modifying
    @Transactional
    @Query("""
    UPDATE ChatMessage m
    SET m.delivered = true
    WHERE m.application = :application
    AND m.sender != :currentUser
    AND m.delivered = false
    AND m.read = false
""")
    void markAllDeliveredForApplication(Application application, User currentUser);

    List<?> findByApplicationOrderBySentAtAsc(Application app);
}
