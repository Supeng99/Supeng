package com.ai.assistant.controller;

import com.ai.assistant.config.AiConfig;
import com.ai.assistant.model.ApiResponse;
import com.ai.assistant.model.ChatRequest;
import com.ai.assistant.service.ChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * AI聊天控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Validated
@Api(tags = "AI聊天接口")
public class ChatController {

    private final ChatService chatService;
    private final AiConfig aiConfig;

    /**
     * SSE流式对话
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation(value = "SSE流式对话", notes = "通过Server-Sent Events实现实时流式输出")
    public SseEmitter chatStream(
            @Valid @RequestBody @ApiParam(value = "聊天请求", required = true) ChatRequest request,
            HttpServletRequest httpRequest) {

        String userId = getUserId(request, httpRequest);
        request.setUserId(userId);

        if (request.getModelType() == null || request.getModelType().isEmpty()) {
            request.setModelType(aiConfig.getDefaultModel());
        }

        log.info("SSE流式对话开始 - userId: {}, model: {}, sessionId: {}",
                maskUserId(userId), request.getModelType(), request.getSessionId());

        return chatService.createStreamEmitter(request);
    }

    /**
     * 非流式对话
     */
    @PostMapping("/non-stream")
    @ApiOperation(value = "非流式对话", notes = "等待完整响应后返回")
    public ApiResponse<String> chatNonStream(
            @Valid @RequestBody @ApiParam(value = "聊天请求", required = true) ChatRequest request,
            HttpServletRequest httpRequest) {

        String userId = getUserId(request, httpRequest);
        request.setUserId(userId);

        if (request.getModelType() == null || request.getModelType().isEmpty()) {
            request.setModelType(aiConfig.getDefaultModel());
        }

        log.info("非流式对话开始 - userId: {}, model: {}", maskUserId(userId), request.getModelType());

        String response = chatService.chat(request);
        return ApiResponse.success(response);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @ApiOperation(value = "健康检查", notes = "检查服务状态和配置")
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        result.put("defaultModel", aiConfig.getDefaultModel());
        result.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ApiResponse.success(result);
    }

    /**
     * 获取用户ID
     */
    private String getUserId(ChatRequest request, HttpServletRequest httpRequest) {
        if (request.getUserId() != null && !request.getUserId().trim().isEmpty()) {
            return request.getUserId();
        }

        String userId = httpRequest.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }

        String remoteAddr = httpRequest.getRemoteAddr();
        return "anonymous:" + (remoteAddr != null ? remoteAddr.hashCode() : "unknown");
    }

    /**
     * 用户ID脱敏
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 4) {
            return "****";
        }
        return userId.substring(0, 2) + "****" + userId.substring(userId.length() - 2);
    }
}
