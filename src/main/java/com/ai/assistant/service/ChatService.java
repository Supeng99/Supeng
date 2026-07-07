package com.ai.assistant.service;

import com.ai.assistant.entity.ChatMessage;
import com.ai.assistant.entity.ChatSession;
import com.ai.assistant.mapper.ChatMessageMapper;
import com.ai.assistant.mapper.ChatSessionMapper;
import com.ai.assistant.model.AiMessage;
import com.ai.assistant.model.ChatRequest;
import com.ai.assistant.model.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final AiModelFactory aiModelFactory;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public SseEmitter createStreamEmitter(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String emitterId = UUID.randomUUID().toString();
        activeEmitters.put(emitterId, emitter);

        emitter.onCompletion(() -> activeEmitters.remove(emitterId));
        emitter.onTimeout(() -> activeEmitters.remove(emitterId));
        emitter.onError(e -> {
            log.warn("SSE error for emitter: {}", emitterId, e);
            activeEmitters.remove(emitterId);
        });

        processStreamRequest(request, emitter, emitterId);
        return emitter;
    }

    public void processStreamRequest(ChatRequest request, SseEmitter emitter, String emitterId) {
        String userId = request.getUserId();
        String modelType = request.getModelType();
        String messageId = UUID.randomUUID().toString();

        try {
            ChatSession session = getOrCreateSession(request, userId, modelType);
            Long sessionId = session.getId();

            saveUserMessage(sessionId, request.getMessage(), modelType);

            List<AiMessage> conversationHistory = getConversationHistory(sessionId);
            String searchContext = "";
            if (Boolean.TRUE.equals(request.getSearchKnowledge())) {
                searchContext = documentService.searchKnowledge(request.getMessage());
                if (!searchContext.isEmpty()) {
                    conversationHistory.add(0, new AiMessage("system",
                        "Please answer based on the following knowledge base content:\n" + searchContext));
                }
            }

            AiModelClient client = aiModelFactory.getClient(modelType);

            StringBuilder fullResponse = new StringBuilder();
            client.chatStream(conversationHistory,
                    chunk -> {
                        fullResponse.append(chunk);
                        try {
                            ChatResponse response = buildResponse(sessionId, messageId, chunk, modelType, false);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(response));
                        } catch (IOException e) {
                            log.warn("Failed to send SSE chunk", e);
                        }
                    },
                    () -> {
                        saveAssistantMessage(sessionId, fullResponse.toString(), modelType, messageId);
                        updateSessionLastMessage(sessionId, fullResponse.toString());
                        try {
                            ChatResponse endResponse = buildResponse(sessionId, messageId, "", modelType, true);
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(endResponse));
                            emitter.complete();
                        } catch (IOException e) {
                            log.warn("Failed to send SSE complete", e);
                            emitter.complete();
                        }
                        activeEmitters.remove(emitterId);
                    },
                    error -> {
                        log.error("Stream error for model {}: {}", modelType, error.getMessage());
                        try {
                            Map<String, String> errorMap = new HashMap<>();
                            errorMap.put("error", error.getMessage());
                            errorMap.put("model", modelType);
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(errorMap));
                            emitter.completeWithError(error);
                        } catch (IOException e) {
                            emitter.completeWithError(error);
                        }
                        activeEmitters.remove(emitterId);
                    });

        } catch (Exception e) {
            log.error("Failed to process stream request for model {}: {}", modelType, e.getMessage(), e);
            try {
                Map<String, String> errorMap = new HashMap<>();
                errorMap.put("error", "请求处理失败: " + e.getMessage());
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(errorMap));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                emitter.completeWithError(e);
            }
            activeEmitters.remove(emitterId);
        }
    }

    private ChatSession getOrCreateSession(ChatRequest request, String userId, String modelType) {
        if (request.getSessionId() != null) {
            ChatSession existing = sessionMapper.selectById(request.getSessionId());
            if (existing != null) {
                return existing;
            }
        }

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(truncateTitle(request.getMessage()));
        session.setModelType(modelType);
        session.setStatus(1);
        session.setMessageCount(0);
        sessionMapper.insert(session);
        return session;
    }

    private String truncateTitle(String message) {
        if (message == null) return "New Chat";
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
    }

    private void saveUserMessage(Long sessionId, String content, String modelType) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("user");
        message.setContent(content);
        message.setModelType(modelType);
        messageMapper.insert(message);
        incrementMessageCount(sessionId);
    }

    private void saveAssistantMessage(Long sessionId, String content, String modelType, String messageId) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setContent(content);
        message.setModelType(modelType);
        message.setTokenCount(estimateTokenCount(content));
        messageMapper.insert(message);
        incrementMessageCount(sessionId);
    }

    private void incrementMessageCount(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setMessageCount(session.getMessageCount() == null ? 1 : session.getMessageCount() + 1);
            sessionMapper.updateById(session);
        }
    }

    private void updateSessionLastMessage(Long sessionId, String lastMessage) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setLastMessage(lastMessage.length() > 200 ? lastMessage.substring(0, 200) : lastMessage);
            sessionMapper.updateById(session);
        }
    }

    private List<AiMessage> getConversationHistory(Long sessionId) {
        List<ChatMessage> messages = messageMapper.selectBySessionId(sessionId);
        return messages.stream()
                .map(m -> new AiMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }

    private ChatResponse buildResponse(Long sessionId, String messageId, String content,
                                       String modelType, Boolean isEnd) {
        ChatResponse response = new ChatResponse();
        response.setSessionId(sessionId);
        response.setMessageId(messageId);
        response.setContent(content);
        response.setRole("assistant");
        response.setModelType(modelType);
        response.setIsEnd(isEnd);
        return response;
    }

    private int estimateTokenCount(String text) {
        return (int) Math.ceil((text != null ? text.length() : 0) / 4.0);
    }

    public String chat(ChatRequest request) {
        String modelType = request.getModelType();
        AiModelClient client = aiModelFactory.getClient(modelType);

        List<AiMessage> messages = new ArrayList<>();
        if (request.getSessionId() != null) {
            messages.addAll(getConversationHistory(request.getSessionId()));
        }

        String searchContext = "";
        if (Boolean.TRUE.equals(request.getSearchKnowledge())) {
            searchContext = documentService.searchKnowledge(request.getMessage());
            if (!searchContext.isEmpty()) {
                messages.add(0, new AiMessage("system",
                    "Please answer based on the following knowledge base content:\n" + searchContext));
            }
        }

        messages.add(new AiMessage("user", request.getMessage()));

        String response = client.chat(messages);
        return response;
    }
}
