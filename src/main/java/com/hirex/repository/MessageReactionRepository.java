package com.hirex.repository;



import com.hirex.entity.ChatMessage;
import com.hirex.entity.MessageReaction;
import com.hirex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageAndUser(ChatMessage message, User user);

    @Modifying
    @Transactional
    void deleteByMessageAndUser(ChatMessage message, User user);

    @Query("SELECT COUNT(r) FROM MessageReaction r WHERE r.message = :message AND r.emoji = :emoji")
    long countByMessageAndEmoji(ChatMessage message, String emoji);
}
