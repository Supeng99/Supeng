package com.ai.assistant.aop;

import com.ai.assistant.model.ApiResponse;
import com.ai.assistant.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private static final String RATE_LIMIT_EXCLUDE_PATHS = "/api/chat/health";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        if (isExcludedPath(path)) {
            return true;
        }

        String userId = extractUserId(request);
        String clientIp = getClientIp(request);

        boolean allowed = rateLimitService.isAllowed(userId, clientIp);

        if (!allowed) {
            log.warn("Rate limit exceeded for user: {}, IP: {}", userId, clientIp);
            sendRateLimitResponse(response, userId);
            return false;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimitService.getRemainingRequests(userId)));

        return true;
    }

    private boolean isExcludedPath(String path) {
        return path.equals(RATE_LIMIT_EXCLUDE_PATHS);
    }

    private String extractUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.trim().isEmpty()) {
            return "auth:" + authHeader.hashCode();
        }

        return "anonymous:" + request.getRemoteAddr().hashCode();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletResponse response, String userId) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error("Rate limit exceeded. Please try again later.");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", 429);
        errorResponse.put("message", apiResponse.getMessage());
        errorResponse.put("data", null);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
