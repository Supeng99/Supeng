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
public class DeepSeekClient implements AiModelClient {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public DeepSeekClient(AiConfig aiConfig, ObjectMapper objectMapper) {
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
        return "deepseek";
    }

    @Override
    public String chat(List<AiMessage> messages) {
        AiConfig.ModelConfig config = aiConfig.getDeepseek();
        String requestBody = buildRequestBody(messages, config.getModel(), false);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DeepSeek API error: " + response);
            }
            String responseBody = response.body().string();
            return parseResponseContent(responseBody);
        } catch (IOException e) {
            log.error("DeepSeek chat failed", e);
            throw new RuntimeException("DeepSeek chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStream(List<AiMessage> messages, Consumer<String> onChunk,
                           Runnable onComplete, Consumer<Exception> onError) {
        AiConfig.ModelConfig config = aiConfig.getDeepseek();
        String requestBody = buildRequestBody(messages, config.getModel(), true);

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("DeepSeek stream failed", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    onError.accept(new IOException("DeepSeek API error: " + response));
                    return;
                }
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        onComplete.run();
                        return;
                    }
                    MediaType contentType = body.contentType();
                    if (contentType != null && "text/event-stream".equals(contentType.type() + "/" + contentType.subtype())) {
                        processStreamResponse(body.byteStream(), onChunk, onComplete, onError);
                    } else {
                        String fullResponse = body.string();
                        onChunk.accept(parseResponseContent(fullResponse));
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
            log.error("Error processing stream response", e);
            onError.accept(e);
        }
    }

    private String parseStreamContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            return root.path("choices")
                    .path(0)
                    .path("delta")
                    .path("content")
                    .asText();
        } catch (Exception e) {
            log.debug("Failed to parse stream chunk: {}", data);
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
            requestMap.put("messages", msgList);
            requestMap.put("stream", stream);

            return objectMapper.writeValueAsString(requestMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request body", e);
        }
    }

    private String parseResponseContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();
        } catch (Exception e) {
            log.error("Failed to parse response: {}", responseBody, e);
            throw new RuntimeException("Failed to parse response", e);
        }
    }
}
