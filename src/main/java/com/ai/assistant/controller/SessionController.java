package com.ai.assistant.controller;

import com.ai.assistant.config.AiConfig;
import com.ai.assistant.entity.ChatMessage;
import com.ai.assistant.entity.ChatSession;
import com.ai.assistant.model.ApiResponse;
import com.ai.assistant.service.SessionService;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AiConfig aiConfig;

    @PostMapping("/create")
    public ApiResponse<ChatSession> createSession(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        try {
            String userId = getUserId(body, request);
            String modelType = body != null ? body.get("modelType") : null;
            if (modelType == null || modelType.isEmpty()) {
                modelType = aiConfig.getDefaultModel();
            }

            ChatSession session = sessionService.createSession(userId, modelType);
            return ApiResponse.success(session);
        } catch (Exception e) {
            log.error("Failed to create session", e);
            return ApiResponse.error("Failed to create session: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<ChatSession> getSession(@PathVariable Long id) {
        try {
            ChatSession session = sessionService.getSession(id);
            if (session == null) {
                return ApiResponse.error("Session not found");
            }
            return ApiResponse.success(session);
        } catch (Exception e) {
            log.error("Failed to get session {}", id, e);
            return ApiResponse.error("Failed to get session: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<ChatSession>> listSessions(
            @RequestParam(required = false, defaultValue = "") String userId,
            HttpServletRequest request) {
        try {
            String resolvedUserId = resolveUserId(userId, request);
            List<ChatSession> sessions = sessionService.getUserSessions(resolvedUserId);
            return ApiResponse.success(sessions);
        } catch (Exception e) {
            log.error("Failed to list sessions", e);
            return ApiResponse.error("Failed to list sessions: " + e.getMessage());
        }
    }

    @GetMapping("/page")
    public ApiResponse<PageInfo<ChatSession>> getSessionsPaged(
            @RequestParam(required = false, defaultValue = "") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        try {
            String resolvedUserId = resolveUserId(userId, request);
            PageInfo<ChatSession> pageInfo = sessionService.getSessionsPaged(resolvedUserId, page, size);
            return ApiResponse.success(pageInfo);
        } catch (Exception e) {
            log.error("Failed to get paged sessions", e);
            return ApiResponse.error("Failed to get sessions: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ChatSession> updateSession(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String title = body != null ? body.get("title") : null;
            ChatSession session = sessionService.updateSession(id, title);
            if (session == null) {
                return ApiResponse.error("Session not found");
            }
            return ApiResponse.success(session);
        } catch (Exception e) {
            log.error("Failed to update session {}", id, e);
            return ApiResponse.error("Failed to update session: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/model")
    public ApiResponse<Void> updateSessionModel(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String modelType = body != null ? body.get("modelType") : null;
            if (modelType == null || modelType.isEmpty()) {
                return ApiResponse.error("modelType is required");
            }
            sessionService.updateSessionModel(id, modelType);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Failed to update session model {}", id, e);
            return ApiResponse.error("Failed to update session: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        try {
            sessionService.deleteSession(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Failed to delete session {}", id, e);
            return ApiResponse.error("Failed to delete session: " + e.getMessage());
        }
    }

    @DeleteMapping("/user/{userId}")
    public ApiResponse<Void> deleteUserSessions(@PathVariable String userId) {
        try {
            sessionService.deleteUserSessions(userId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Failed to delete user sessions {}", userId, e);
            return ApiResponse.error("Failed to delete sessions: " + e.getMessage());
        }
    }

    @GetMapping("/messages")
    public ApiResponse<List<ChatMessage>> getMessagesBySessionId(
            @RequestParam Long sessionId) {
        try {
            List<ChatMessage> messages = sessionService.getSessionMessages(sessionId);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            log.error("Failed to get messages for session {}", sessionId, e);
            return ApiResponse.error("Failed to get messages: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/messages/page")
    public ApiResponse<PageInfo<ChatMessage>> getSessionMessagesPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            PageInfo<ChatMessage> pageInfo = sessionService.getSessionMessagesPaged(id, page, size);
            return ApiResponse.success(pageInfo);
        } catch (Exception e) {
            log.error("Failed to get paged messages for session {}", id, e);
            return ApiResponse.error("Failed to get messages: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archiveSession(@PathVariable Long id) {
        try {
            sessionService.archiveSession(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Failed to archive session {}", id, e);
            return ApiResponse.error("Failed to archive session: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/unarchive")
    public ApiResponse<Void> unarchiveSession(@PathVariable Long id) {
        try {
            sessionService.unarchiveSession(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Failed to unarchive session {}", id, e);
            return ApiResponse.error("Failed to unarchive session: " + e.getMessage());
        }
    }

    @GetMapping("/count")
    public ApiResponse<Map<String, Object>> getSessionCount(
            @RequestParam(required = false, defaultValue = "") String userId,
            HttpServletRequest request) {
        try {
            String resolvedUserId = resolveUserId(userId, request);
            long count = sessionService.getUserSessionCount(resolvedUserId);
            Map<String, Object> result = new HashMap<>();
            result.put("userId", resolvedUserId);
            result.put("count", count);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to get session count", e);
            return ApiResponse.error("Failed to get session count: " + e.getMessage());
        }
    }

    private String resolveUserId(String userId, HttpServletRequest request) {
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }
        return getUserId(null, request);
    }

    private String getUserId(Map<String, String> body, HttpServletRequest request) {
        if (body != null && body.containsKey("userId") &&
                body.get("userId") != null && !body.get("userId").trim().isEmpty()) {
            return body.get("userId");
        }

        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }

        String remoteAddr = request.getRemoteAddr();
        return "anonymous:" + (remoteAddr != null ? remoteAddr.hashCode() : "unknown");
    }
}
