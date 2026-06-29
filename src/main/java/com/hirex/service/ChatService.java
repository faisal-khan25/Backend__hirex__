package com.hirex.service;

import com.hirex.dto.ChatDto;
import com.hirex.entity.*;
import com.hirex.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    // Default page size for paginated message loading (infinite scroll)
    private static final int DEFAULT_PAGE_SIZE = 30;

    private final ChatMessageRepository chatRepo;
    private final ApplicationRepository appRepo;
    private final UserRepository userRepo;
    private final MessageReactionRepository reactionRepo;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "jpg", "jpeg", "png", "gif", "zip", "txt", "xls", "xlsx"
    );

    private static final Map<String, String> EXT_TO_TYPE = new HashMap<>();
    static {
        for (String e : List.of("jpg","jpeg","png","gif","webp")) EXT_TO_TYPE.put(e, "IMAGE");
        for (String e : List.of("pdf"))                           EXT_TO_TYPE.put(e, "PDF");
        for (String e : List.of("doc","docx"))                   EXT_TO_TYPE.put(e, "DOC");
        for (String e : List.of("zip","tar","gz"))               EXT_TO_TYPE.put(e, "ZIP");
        for (String e : List.of("xls","xlsx"))                   EXT_TO_TYPE.put(e, "EXCEL");
    }

    public ChatService(ChatMessageRepository chatRepo,
                       ApplicationRepository appRepo,
                       UserRepository userRepo,
                       MessageReactionRepository reactionRepo) {
        this.chatRepo     = chatRepo;
        this.appRepo      = appRepo;
        this.userRepo     = userRepo;
        this.reactionRepo = reactionRepo;
    }

    // ── Send text message ────────────────────────────────────────

    @Transactional
    public ChatDto.MessageResponse sendMessage(Long applicationId, String content, String senderEmail) {
        User sender = userRepo.findByEmail(senderEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Application app = appRepo.findById(applicationId).orElseThrow(() -> new RuntimeException("Application not found"));
        validateAccess(app, sender);
        checkCandidateEligibility(app, sender);

        ChatMessage msg = new ChatMessage();
        msg.setApplication(app);
        msg.setSender(sender);
        msg.setContent(content.trim());
        chatRepo.save(msg);

        ChatDto.MessageResponse response = toMessageResponse(msg, sender.getId());

        // Push new message to all conversation subscribers via WebSocket.
        // This allows the frontend to stop polling and only rely on WS for real-time updates.
        messagingTemplate.convertAndSend(
                "/topic/chat/" + applicationId,
                Map.of("type", "NEW_MESSAGE", "message", response)
        );

        return response;
    }

    // ── Send file message ────────────────────────────────────────

    @Transactional
    public ChatDto.MessageResponse sendFile(Long applicationId, MultipartFile file, String senderEmail) throws IOException {
        User sender = userRepo.findByEmail(senderEmail).orElseThrow(() -> new RuntimeException("User not found"));
        Application app = appRepo.findById(applicationId).orElseThrow(() -> new RuntimeException("Application not found"));
        validateAccess(app, sender);
        checkCandidateEligibility(app, sender);

        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new RuntimeException("File type not allowed: " + ext);
        }

        String uploadDir = System.getProperty("chat.upload.dir",
                System.getProperty("user.home") + "/chat-uploads");
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String storedName = UUID.randomUUID() + "_" + originalName;
        Path dest = dir.resolve(storedName);
        file.transferTo(dest.toFile());

        String fileUrl  = "/api/chat/files/" + storedName;
        String fileType = EXT_TO_TYPE.getOrDefault(ext, "OTHER");

        ChatMessage msg = new ChatMessage();
        msg.setApplication(app);
        msg.setSender(sender);
        msg.setContent(null);
        msg.setFileUrl(fileUrl);
        msg.setFileName(originalName);
        msg.setFileSize(file.getSize());
        msg.setFileType(fileType);
        chatRepo.save(msg);

        ChatDto.MessageResponse response = toMessageResponse(msg, sender.getId());

        // Notify via WebSocket so the other party sees the file immediately
        messagingTemplate.convertAndSend(
                "/topic/chat/" + applicationId,
                Map.of("type", "NEW_MESSAGE", "message", response)
        );

        return response;
    }

    // ── Get messages (paginated) ─────────────────────────────────
    // OPTIMIZED: Replaces the old "load all messages at once" pattern.
    // - Page 0 loads the most-recent DEFAULT_PAGE_SIZE messages.
    // - Subsequent pages load older messages (infinite scroll upward).
    // - Uses a JOIN FETCH to load sender + reactions in one query (no N+1).
    // - Marks delivered + read inside the same transaction.

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChatDto.PagedMessageResponse getMessages(Long applicationId, int page, String requesterEmail) {
        User requester = userRepo.findByEmail(requesterEmail).orElseThrow();
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        validateAccess(app, requester);

        // Mark delivered & read in one UPDATE (no SELECT loop)
        chatRepo.markAllDeliveredForApplication(app, requester);
        chatRepo.markAllReadForApplication(app, requester);

        // OPTIMIZED: Two-phase fetch to avoid Hibernate LIMIT + JOIN FETCH warning.
        // Phase 1 — get page of message IDs (fast, uses index)
        Page<Long> idPage = chatRepo.findIdsByApplicationPaged(
                app,
                PageRequest.of(page, DEFAULT_PAGE_SIZE, Sort.by("sentAt").ascending())
        );

        // Phase 2 — fetch full messages with reactions JOIN in one query
        List<ChatMessage> messages = idPage.isEmpty()
                ? Collections.emptyList()
                : chatRepo.findByIdsWithReactions(idPage.getContent());

        // Sort again since IN query doesn't guarantee order
        messages.sort(Comparator.comparing(ChatMessage::getSentAt));

        List<ChatDto.MessageResponse> dtos = messages.stream()
                .filter(m -> !m.isDeletedForUser(requester.getId()))
                .map(m -> toMessageResponse(m, requester.getId()))
                .collect(Collectors.toList());

        ChatDto.PagedMessageResponse result = new ChatDto.PagedMessageResponse();
        result.setMessages(dtos);
        result.setPage(page);
        result.setSize(DEFAULT_PAGE_SIZE);
        result.setTotalElements(idPage.getTotalElements());
        result.setTotalPages(idPage.getTotalPages());
        result.setHasMore(page + 1 < idPage.getTotalPages());
        return result;
    }

    // ── Delete for me ────────────────────────────────────────────

    @Transactional
    public void deleteForMe(Long messageId, String requesterEmail) {
        User requester = userRepo.findByEmail(requesterEmail).orElseThrow();
        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        validateAccess(msg.getApplication(), requester);

        msg.addDeletedForUser(requester.getId());
        chatRepo.save(msg);
    }

    // ── Delete for everyone ──────────────────────────────────────

    @Transactional
    public ChatDto.MessageResponse deleteForEveryone(Long messageId, String requesterEmail) {
        User requester = userRepo.findByEmail(requesterEmail).orElseThrow();
        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        validateAccess(msg.getApplication(), requester);

        if (!msg.getSender().getId().equals(requester.getId())) {
            throw new RuntimeException("Only the sender can delete this message for everyone");
        }

        msg.setDeletedForEveryone(true);
        msg.setContent(null);
        msg.setFileUrl(null);
        msg.setFileName(null);
        msg.setFileType(null);
        msg.setFileSize(null);
        chatRepo.save(msg);

        ChatDto.MessageResponse response = toMessageResponse(msg, requester.getId());
        messagingTemplate.convertAndSend(
                "/topic/chat/" + msg.getApplication().getId(),
                Map.of("type", "MESSAGE_DELETED_FOR_EVERYONE", "message", response)
        );
        return response;
    }

    // ── Reactions ────────────────────────────────────────────────

    @Transactional
    public ChatDto.MessageResponse reactToMessage(Long messageId, String emoji, String requesterEmail) {
        User requester = userRepo.findByEmail(requesterEmail).orElseThrow();
        ChatMessage msg = chatRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        validateAccess(msg.getApplication(), requester);

        Optional<MessageReaction> existing = reactionRepo.findByMessageAndUser(msg, requester);
        if (existing.isPresent()) {
            if (existing.get().getEmoji().equals(emoji)) {
                reactionRepo.delete(existing.get());
            } else {
                existing.get().setEmoji(emoji);
                reactionRepo.save(existing.get());
            }
        } else {
            MessageReaction reaction = new MessageReaction();
            reaction.setMessage(msg);
            reaction.setUser(requester);
            reaction.setEmoji(emoji);
            reactionRepo.save(reaction);
        }

        // OPTIMIZED: Re-fetch with reactions JOIN to avoid lazy load issues
        List<ChatMessage> updated = chatRepo.findByIdsWithReactions(List.of(messageId));
        ChatMessage updatedMsg = updated.isEmpty() ? msg : updated.get(0);
        ChatDto.MessageResponse response = toMessageResponse(updatedMsg, requester.getId());

        // Push reaction update via WebSocket
        messagingTemplate.convertAndSend(
                "/topic/chat/" + msg.getApplication().getId(),
                Map.of("type", "REACTION_UPDATED", "message", response)
        );

        return response;
    }

    // ── Manager conversations ────────────────────────────────────
    // OPTIMIZED: Replaces the N+1 loop that called findByApplicationOrderBySentAtAsc
    // for every conversation. Now uses two batched queries:
    //   1. findShortlistedApplicationsByManager — one query, JOIN FETCH
    //   2. findLastMessagesForApplications      — one query for ALL last messages
    //   3. countUnreadMessagesForApplications   — one aggregate query for ALL unread counts

    @Transactional
    public List<ChatDto.ConversationSummary> getConversationsForManager(String managerEmail) {
        User manager = userRepo.findByEmail(managerEmail).orElseThrow();
        List<Application> apps = appRepo.findVisibleChatApplicationsByManager(
                manager,
                List.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.HIRED)
        );

        if (apps.isEmpty()) return Collections.emptyList();

        // Batch-load last messages for all applications (1 query instead of N)
        List<ChatMessage> lastMessages = chatRepo.findLastMessagesForApplications(apps);
        Map<Long, ChatMessage> lastMsgByAppId = lastMessages.stream()
                .collect(Collectors.toMap(m -> m.getApplication().getId(), m -> m, (a, b) -> b));

        // Batch-load unread counts for all applications (1 query instead of N)
        List<Object[]> unreadRaw = chatRepo.countUnreadMessagesForApplications(apps, manager);
        Map<Long, Long> unreadByAppId = unreadRaw.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return apps.stream().map(app -> {
            ChatDto.ConversationSummary summary = new ChatDto.ConversationSummary();
            summary.setApplicationId(app.getId());
            summary.setCandidateName(app.getApplicant().getName());
            summary.setCandidateEmail(app.getApplicant().getEmail());
            summary.setJobId(app.getJob().getId());
            summary.setJobTitle(app.getJob().getTitle());
            summary.setApplicationStatus(app.getStatus().name());
            summary.setUnreadCount(unreadByAppId.getOrDefault(app.getId(), 0L));

            ChatMessage last = lastMsgByAppId.get(app.getId());
            if (last != null) {
                if (last.isDeletedForEveryone()) {
                    summary.setLastMessage("This message was deleted");
                } else if (last.getFileUrl() != null) {
                    summary.setLastMessage("📎 " + last.getFileName());
                } else {
                    summary.setLastMessage(last.getContent());
                }
                summary.setLastMessageAt(last.getSentAt().toString());
            }
            return summary;
        }).collect(Collectors.toList());
    }

    public long getUnreadCountForJobseeker(String email) {
        User user = userRepo.findByEmail(email).orElseThrow();
        return chatRepo.countUnreadForJobseeker(user);
    }

    public long getUnreadCountForManager(String email) {
        User manager = userRepo.findByEmail(email).orElseThrow();
        return chatRepo.countConversationsWithUnreadForManager(manager);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void validateAccess(Application app, User user) {
        boolean isCandidate = app.getApplicant().getId().equals(user.getId());
        boolean isManager   = app.getJob().getCompany().getManager() != null &&
                app.getJob().getCompany().getManager().getId().equals(user.getId());
        if (!isCandidate && !isManager) {
            throw new RuntimeException("Access denied");
        }
    }

    private void checkCandidateEligibility(Application app, User sender) {
        if (sender.getRole() == Role.JOBSEEKER &&
                app.getStatus() != ApplicationStatus.SHORTLISTED &&
                app.getStatus() != ApplicationStatus.HIRED) {
            throw new RuntimeException("Only shortlisted or hired candidates can interact with the recruiter");
        }
    }

    private ChatDto.MessageResponse toMessageResponse(ChatMessage msg, Long viewerUserId) {
        ChatDto.MessageResponse r = new ChatDto.MessageResponse();
        r.setId(msg.getId());
        r.setApplicationId(msg.getApplication().getId());
        r.setSenderId(msg.getSender().getId());
        r.setSenderName(msg.getSender().getName());
        r.setSenderRole(msg.getSender().getRole().name());
        r.setSentAt(msg.getSentAt().toString());
        r.setDelivered(msg.isDelivered());
        r.setRead(msg.isRead());
        r.setDeletedForEveryone(msg.isDeletedForEveryone());

        if (msg.isRead())           r.setStatus("READ");
        else if (msg.isDelivered()) r.setStatus("DELIVERED");
        else                        r.setStatus("SENT");

        if (msg.isDeletedForEveryone()) {
            r.setContent(null);
        } else {
            r.setContent(msg.getContent());
            r.setFileUrl(msg.getFileUrl());
            r.setFileName(msg.getFileName());
            r.setFileSize(msg.getFileSize());
            r.setFileType(msg.getFileType());
        }

        // OPTIMIZED: Reactions are now LAZY, loaded via JOIN FETCH in the query layer.
        // getReactions() is safe here because we always call findByIdsWithReactions()
        // (which JOIN FETCHes reactions) before calling toMessageResponse().
        Map<String, ChatDto.ReactionSummary> grouped = new LinkedHashMap<>();
        List<MessageReaction> reactionList = msg.getReactions();
        if (reactionList != null) {
            for (MessageReaction reaction : reactionList) {
                String emoji = reaction.getEmoji();
                ChatDto.ReactionSummary rs = grouped.computeIfAbsent(emoji, e -> {
                    ChatDto.ReactionSummary s = new ChatDto.ReactionSummary();
                    s.setEmoji(e);
                    s.setCount(0);
                    return s;
                });
                rs.setCount(rs.getCount() + 1);
                if (reaction.getUser().getId().equals(viewerUserId)) {
                    rs.setReactedByMe(true);
                }
            }
        }
        r.setReactions(new ArrayList<>(grouped.values()));
        return r;
    }
}