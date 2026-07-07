package com.ai.assistant.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class ChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sessionId;
    private String messageId;
    private String content;
    private String role;
    private String modelType;
    private Integer tokenCount;
    private String citations;
    private Boolean isEnd;
}
