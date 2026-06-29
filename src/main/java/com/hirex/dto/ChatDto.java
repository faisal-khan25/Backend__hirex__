package com.hirex.dto;

import java.util.List;

public class ChatDto {

    // ── Reaction summary returned inside MessageResponse ────────
    public static class ReactionSummary {
        private String emoji;
        private long count;
        private boolean reactedByMe;

        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }

        public boolean isReactedByMe() { return reactedByMe; }
        public void setReactedByMe(boolean reactedByMe) { this.reactedByMe = reactedByMe; }
    }

    // ── Message response (sent to client) ───────────────────────
    public static class MessageResponse {
        private Long id;
        private Long applicationId;
        private Long senderId;
        private String senderName;
        private String senderRole;
        private String content;
        private String sentAt;
        private boolean read;
        private boolean delivered;
        private String status;
        private boolean deletedForEveryone;
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String fileType;
        private List<ReactionSummary> reactions;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getApplicationId() { return applicationId; }
        public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }

        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }

        public String getSenderRole() { return senderRole; }
        public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getSentAt() { return sentAt; }
        public void setSentAt(String sentAt) { this.sentAt = sentAt; }

        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }

        public boolean isDelivered() { return delivered; }
        public void setDelivered(boolean delivered) { this.delivered = delivered; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

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

        public List<ReactionSummary> getReactions() { return reactions; }
        public void setReactions(List<ReactionSummary> reactions) { this.reactions = reactions; }
    }

    // ── Paginated message response ───────────────────────────────
    public static class PagedMessageResponse {
        private List<MessageResponse> messages;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasMore;

        public List<MessageResponse> getMessages() { return messages; }
        public void setMessages(List<MessageResponse> messages) { this.messages = messages; }

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }

        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

        public boolean isHasMore() { return hasMore; }
        public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    }

    // ── Conversation summary (sidebar list) ─────────────────────
    public static class ConversationSummary {
        private Long applicationId;
        private String candidateName;
        private String candidateEmail;
        private Long jobId;
        private String jobTitle;
        private String lastMessage;
        private String lastMessageAt;
        private long unreadCount;
        private String applicationStatus;

        public Long getApplicationId() { return applicationId; }
        public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

        public String getCandidateName() { return candidateName; }
        public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

        public String getCandidateEmail() { return candidateEmail; }
        public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }

        public Long getJobId() { return jobId; }
        public void setJobId(Long jobId) { this.jobId = jobId; }

        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

        public String getLastMessageAt() { return lastMessageAt; }
        public void setLastMessageAt(String lastMessageAt) { this.lastMessageAt = lastMessageAt; }

        public long getUnreadCount() { return unreadCount; }
        public void setUnreadCount(long unreadCount) { this.unreadCount = unreadCount; }

        public String getApplicationStatus() { return applicationStatus; }
        public void setApplicationStatus(String applicationStatus) { this.applicationStatus = applicationStatus; }
    }

    // ── Request: send text message ───────────────────────────────
    public static class SendMessageRequest {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    // ── Request: add / update reaction ──────────────────────────
    public static class ReactRequest {
        private String emoji;
        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }
    }

    // ── Unread count response ────────────────────────────────────
    public static class UnreadCountResponse {
        private long count;
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}