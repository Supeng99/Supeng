package com.ai.assistant.service;

import com.ai.assistant.model.AiMessage;
import java.util.List;
import java.util.function.Consumer;

public interface AiModelClient {

    String getModelType();

    String chat(List<AiMessage> messages);

    void chatStream(List<AiMessage> messages, Consumer<String> onChunk, Runnable onComplete, Consumer<Exception> onError);
}
