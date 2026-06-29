package com.hirex.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores individual chat messages tied to an Application.
 * OPTIMIZED: Added composite indexes for frequent query patterns.
 *            Reactions changed from EAGER to LAZY to avoid N+1 on bulk loads.
 */
@Entity
@Table(
    name = "chat_messages",
    indexes = {
        // Primary query: fetch all messages for a chat, sorted by time
        @Index(name = "idx_chat_application_sent", columnList = "application_id, sent_at ASC"),
        // Unread count queries
        @Index(name = "idx_chat_sender_read", columnList = "sender_id, is_read"),
        // Last-message lookup for conversation summaries
        @Index(name = "idx_chat_application_id", columnList = "application_id")
    }
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "is_read")
    private boolean read = false;

    @Column(name = "is_delivered")
    private boolean delivered = false;

    @Column(name = "deleted_for_user_ids", length = 500)
    private String deletedForUserIds = "";

    @Column(name = "deleted_for_everyone")
    private boolean deletedForEveryone = false;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type")
    private String fileType;

    // OPTIMIZED: Changed from EAGER to LAZY to prevent automatic JOIN on every message fetch.
    // ChatService now uses a dedicated query to load reactions only when needed.
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MessageReaction> reactions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        sentAt = LocalDateTime.now();
        if (deletedForUserIds == null) deletedForUserIds = "";
    }

    public ChatMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public String getDeletedForUserIds() { return deletedForUserIds; }
    public void setDeletedForUserIds(String deletedForUserIds) { this.deletedForUserIds = deletedForUserIds; }

    public boolean isDeletedForEveryone() { return deletedForEveryone; }
    public void setDeletedForEveryone(boolean deletedForEveryone) { this.deletedForEveryone = deletedForEveryone; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public List<MessageReaction> getReactions() { return reactions; }
    public void setReactions(List<MessageReaction> reactions) { this.reactions = reactions; }

    public boolean isDeletedForUser(Long userId) {
        if (deletedForUserIds == null || deletedForUserIds.isBlank()) return false;
        for (String id : deletedForUserIds.split(",")) {
            if (id.trim().equals(String.valueOf(userId))) return true;
        }
        return false;
    }

    public void addDeletedForUser(Long userId) {
        if (isDeletedForUser(userId)) return;
        if (deletedForUserIds == null || deletedForUserIds.isBlank()) {
            deletedForUserIds = String.valueOf(userId);
        } else {
            deletedForUserIds = deletedForUserIds + "," + userId;
        }
    }
}
