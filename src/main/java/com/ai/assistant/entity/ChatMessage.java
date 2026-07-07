package com.ai.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String modelType;

    private Integer tokenCount;

    private String citations;

    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
