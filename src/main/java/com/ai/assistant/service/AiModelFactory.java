package com.ai.assistant.service;

import com.ai.assistant.config.AiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiModelFactory {

    @Autowired
    private AiConfig aiConfig;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private QwenClient qwenClient;

    private final Map<String, AiModelClient> clientMap = new ConcurrentHashMap<>();

    @Autowired
    public void initClients() {
        clientMap.put("deepseek", deepSeekClient);
        clientMap.put("qwen", qwenClient);
    }

    public AiModelClient getClient(String modelType) {
        AiModelClient client = clientMap.get(modelType);
        if (client == null) {
            String defaultModel = aiConfig.getDefaultModel();
            client = clientMap.get(defaultModel);
            if (client == null) {
                client = deepSeekClient;
            }
        }
        return client;
    }
}
