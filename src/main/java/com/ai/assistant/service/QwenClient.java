package com.ai.assistant.service;

import com.ai.assistant.config.AiConfig;
import com.ai.assistant.model.AiMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QwenClient implements AiModelClient {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public QwenClient(AiConfig aiConfig, ObjectMapper objectMapper) {
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getModelType() {
        return "qwen";
    }

    @Override
    public String chat(List<AiMessage> messages) {
        AiConfig.ModelConfig config = aiConfig.getQwen();
        String requestBody = buildRequestBody(messages, config.getModel(), false);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/services/aigc/text-generation/generation")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Qwen API error: " + response);
            }
            String responseBody = response.body().string();
            return parseResponseContent(responseBody);
        } catch (IOException e) {
            log.error("Qwen chat failed", e);
            throw new RuntimeException("Qwen chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStream(List<AiMessage> messages, Consumer<String> onChunk,
                          Runnable onComplete, Consumer<Exception> onError) {
        log.info("QwenClient.chatStream called with {} messages", messages.size());
        AiConfig.ModelConfig config = aiConfig.getQwen();
        String requestBody = buildRequestBody(messages, config.getModel(), true);
        
        log.info("Qwen API Request - URL: {}, model: {}", config.getBaseUrl() + "/services/aigc/text-generation/generation", config.getModel());

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/services/aigc/text-generation/generation")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Qwen stream failed", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                log.info("Qwen onResponse - isSuccessful: {}, code: {}", response.isSuccessful(), response.code());
                if (!response.isSuccessful()) {
                    onError.accept(new IOException("Qwen API error: " + response));
                    return;
                }
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        log.warn("Qwen response body is null");
                        onComplete.run();
                        return;
                    }
                    MediaType contentType = body.contentType();
                    log.info("Qwen response contentType: {}", contentType);
                    if (contentType != null && "text/event-stream".equals(contentType.type() + "/" + contentType.subtype())) {
                        log.info("Processing as SSE stream");
                        processStreamResponse(body.byteStream(), onChunk, onComplete, onError);
                    } else {
                        String fullResponse = body.string();
                        log.info("Non-SSE response (JSON), length: {}, content: {}", fullResponse.length(), fullResponse);
                        String content = parseResponseContent(fullResponse);
                        log.info("Parsed content: {}", content);
                        if (content != null && !content.isEmpty()) {
                            onChunk.accept(content);
                        }
                        onComplete.run();
                    }
                }
            }
        });
    }

    private void processStreamResponse(InputStream inputStream, Consumer<String> onChunk,
                                       Runnable onComplete, Consumer<Exception> onError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String content = parseStreamContent(data);
                    if (content != null && !content.isEmpty()) {
                        onChunk.accept(content);
                    }
                }
            }
            onComplete.run();
        } catch (Exception e) {
            log.error("Error processing Qwen stream response", e);
            onError.accept(e);
        }
    }

    private String parseStreamContent(String data) {
        try {
            log.info("Raw SSE data: {}", data);
            JsonNode root = objectMapper.readTree(data);
            
            // 打印完整结构用于调试
            log.debug("Parsed JSON structure: {}", root.toString());
            
            // 尝试多种可能的路径
            // 路径1: output.choices[0].delta.content[0].text (百炼 SSE 格式)
            JsonNode deltaNode = root.path("output").path("choices").path(0).path("delta");
            if (!deltaNode.isMissingNode()) {
                log.debug("Found delta node: {}", deltaNode.toString());
                String text = deltaNode.path("content").path(0).path("text").asText("");
                if (!text.isEmpty()) return text;
                text = deltaNode.path("content").asText("");
                if (!text.isEmpty()) return text;
            }
            
            // 路径2: output.choices[0].message.content[0].text
            JsonNode msgNode = root.path("output").path("choices").path(0).path("message");
            if (!msgNode.isMissingNode()) {
                log.debug("Found message node: {}", msgNode.toString());
                String text = msgNode.path("content").path(0).path("text").asText("");
                if (!text.isEmpty()) return text;
                text = msgNode.path("content").asText("");
                if (!text.isEmpty()) return text;
            }
            
            // 路径3: output.text (简单格式)
            String simpleText = root.path("output").path("text").asText("");
            if (!simpleText.isEmpty()) return simpleText;
            
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Qwen stream chunk: {}, error: {}", data, e.getMessage());
            return null;
        }
    }

    private String buildRequestBody(List<AiMessage> messages, String model, boolean stream) {
        try {
            List<Map<String, String>> msgList = messages.stream()
                    .map(m -> {
                        Map<String, String> msgMap = new HashMap<>();
                        msgMap.put("role", m.getRole());
                        msgMap.put("content", m.getContent());
                        return msgMap;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", model);
            Map<String, Object> inputMap = new HashMap<>();
            inputMap.put("messages", msgList);
            requestMap.put("input", inputMap);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("stream", stream);
            parameters.put("result_format", "message");
            requestMap.put("parameters", parameters);

            return objectMapper.writeValueAsString(requestMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    private String parseResponseContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            log.debug("Parse response structure: {}", root.toString());
            
            // 百炼标准格式: output.choices[0].message.content[0].text
            JsonNode textNode = root.path("output")
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .path(0)
                    .path("text");
            
            if (!textNode.isMissingNode()) {
                String text = textNode.asText();
                log.info("Parsed text from content[0].text: {}", text);
                return text;
            }
            
            // 备用格式: output.choices[0].message.content (直接字符串)
            JsonNode directContent = root.path("output")
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");
            if (!directContent.isMissingNode()) {
                if (directContent.isTextual()) {
                    String text = directContent.asText();
                    log.info("Parsed text from direct content: {}", text);
                    return text;
                }
            }
            
            // 备用格式: output.text
            String simpleText = root.path("output").path("text").asText();
            if (!simpleText.isEmpty()) {
                log.info("Parsed text from output.text: {}", simpleText);
                return simpleText;
            }
            
            // 输出完整的响应结构用于调试
            log.warn("Could not parse response, full structure: {}", root.toString());
            return "";
        } catch (Exception e) {
            log.error("Failed to parse response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }
}
