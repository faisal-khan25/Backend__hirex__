package com.hirex.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores a single emoji reaction by a user on a ChatMessage.
 * One user can have at most one reaction per message (enforced by unique constraint).
 */
@Entity
@Table(
        name = "message_reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"})
)
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The emoji character, e.g. "👍", "❤️", "😂" */
    @Column(nullable = false, length = 10)
    private String emoji;

    @Column(name = "reacted_at")
    private LocalDateTime reactedAt;

    @PrePersist
    public void prePersist() {
        reactedAt = LocalDateTime.now();
    }

    public MessageReaction() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ChatMessage getMessage() { return message; }
    public void setMessage(ChatMessage message) { this.message = message; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public LocalDateTime getReactedAt() { return reactedAt; }
    public void setReactedAt(LocalDateTime reactedAt) { this.reactedAt = reactedAt; }
}
