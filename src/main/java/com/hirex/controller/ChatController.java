package com.hirex.controller;

import com.hirex.dto.ChatDto;
import com.hirex.service.ChatService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ── Send text message ────────────────────────────────────────

    @PostMapping("/application/{applicationId}/messages")
    public ResponseEntity<ChatDto.MessageResponse> sendMessage(
            @PathVariable Long applicationId,
            @RequestBody ChatDto.SendMessageRequest req,
            Principal principal) {
        return ResponseEntity.ok(chatService.sendMessage(applicationId, req.getContent(), principal.getName()));
    }

    // ── Upload file/image ────────────────────────────────────────

    @PostMapping("/application/{applicationId}/upload")
    public ResponseEntity<ChatDto.MessageResponse> uploadFile(
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file,
            Principal principal) throws IOException {
        return ResponseEntity.ok(chatService.sendFile(applicationId, file, principal.getName()));
    }

    // ── Serve uploaded files ─────────────────────────────────────
    // OPTIMIZED: Added HTTP cache headers so browsers cache static chat files.
    // Images are cached for 7 days (immutable UUIDs never change).
    // Documents are cached for 1 day. This eliminates repeated downloads.

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String filename,
            @RequestParam(value = "download", defaultValue = "false") boolean download)
            throws MalformedURLException {

        String uploadDir = System.getProperty("chat.upload.dir",
                System.getProperty("user.home") + "/chat-uploads");
        Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(filename).normalize();

        // Prevent path traversal
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!filePath.startsWith(uploadRoot)) {
            return ResponseEntity.badRequest().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String lower = filename.toLowerCase();
        String contentType = "application/octet-stream";
        boolean isImage = false;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) { contentType = "image/jpeg"; isImage = true; }
        else if (lower.endsWith(".png"))  { contentType = "image/png";  isImage = true; }
        else if (lower.endsWith(".gif"))  { contentType = "image/gif";  isImage = true; }
        else if (lower.endsWith(".pdf"))  contentType = "application/pdf";
        else if (lower.endsWith(".doc"))  contentType = "application/msword";
        else if (lower.endsWith(".docx")) contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if (lower.endsWith(".xls"))  contentType = "application/vnd.ms-excel";
        else if (lower.endsWith(".xlsx")) contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        else if (lower.endsWith(".zip"))  contentType = "application/zip";
        else if (lower.endsWith(".txt"))  contentType = "text/plain";

        String disposition = (download || !isImage)
                ? "attachment; filename=\"" + filename + "\""
                : "inline; filename=\"" + filename + "\"";

        // OPTIMIZED: UUID-prefixed filenames are immutable — safe to cache aggressively.
        CacheControl cacheControl = isImage
                ? CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic()
                : CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .cacheControl(cacheControl)
                .body(resource);
    }

    // ── Get messages (paginated) ─────────────────────────────────
    // OPTIMIZED: Now accepts a `page` query param (default 0 = newest page).
    // Frontend uses this for infinite scroll — loads page 0 on open,
    // then increments page as the user scrolls up to load older messages.

    @GetMapping("/application/{applicationId}/messages")
    public ResponseEntity<ChatDto.PagedMessageResponse> getMessages(
            @PathVariable Long applicationId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Principal principal) {
        return ResponseEntity.ok(chatService.getMessages(applicationId, page, principal.getName()));
    }

    // ── Delete for me ────────────────────────────────────────────

    @DeleteMapping("/messages/{messageId}/for-me")
    public ResponseEntity<Void> deleteForMe(
            @PathVariable Long messageId,
            Principal principal) {
        chatService.deleteForMe(messageId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    // ── Delete for everyone ──────────────────────────────────────

    @DeleteMapping("/messages/{messageId}/for-everyone")
    public ResponseEntity<ChatDto.MessageResponse> deleteForEveryone(
            @PathVariable Long messageId,
            Principal principal) {
        return ResponseEntity.ok(chatService.deleteForEveryone(messageId, principal.getName()));
    }

    // ── Add / change / remove reaction ──────────────────────────

    @PostMapping("/messages/{messageId}/react")
    public ResponseEntity<ChatDto.MessageResponse> react(
            @PathVariable Long messageId,
            @RequestBody ChatDto.ReactRequest req,
            Principal principal) {
        return ResponseEntity.ok(chatService.reactToMessage(messageId, req.getEmoji(), principal.getName()));
    }

    // ── Manager: list all shortlisted conversations ──────────────

    @GetMapping("/manager/conversations")
    public ResponseEntity<List<ChatDto.ConversationSummary>> getConversations(Principal principal) {
        return ResponseEntity.ok(chatService.getConversationsForManager(principal.getName()));
    }

    // ── Unread badges ────────────────────────────────────────────

    @GetMapping("/jobseeker/unread-count")
    public ResponseEntity<ChatDto.UnreadCountResponse> jobseekerUnread(Principal principal) {
        ChatDto.UnreadCountResponse r = new ChatDto.UnreadCountResponse();
        r.setCount(chatService.getUnreadCountForJobseeker(principal.getName()));
        return ResponseEntity.ok(r);
    }

    @GetMapping("/manager/unread-count")
    public ResponseEntity<ChatDto.UnreadCountResponse> managerUnread(Principal principal) {
        ChatDto.UnreadCountResponse r = new ChatDto.UnreadCountResponse();
        r.setCount(chatService.getUnreadCountForManager(principal.getName()));
        return ResponseEntity.ok(r);
    }
}
