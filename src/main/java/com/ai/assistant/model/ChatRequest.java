package com.ai.assistant.model;

import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 聊天请求模型
 */
@Data
public class ChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID，新建会话时为null
     */
    private Long sessionId;

    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空", groups = {ChatRequest.Group.Session.class})
    @Length(max = 64, message = "用户ID长度不能超过64")
    private String userId;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空", groups = {ChatRequest.Group.Message.class})
    @Length(min = 1, max = 4000, message = "消息长度需在1-4000字之间")
    private String message;

    /**
     * AI模型类型: deepseek/qwen
     */
    @Pattern(regexp = "^(deepseek|qwen)$", message = "不支持的AI模型类型")
    private String modelType;

    /**
     * 是否搜索知识库
     */
    private Boolean searchKnowledge;

    /**
     * 历史消息数量限制
     */
    @Range(min = 0, max = 20, message = "历史消息数量需在0-20之间")
    private Integer historyLimit;

    /**
     * 校验分组
     */
    public interface Group {
        interface Session {}
        interface Message {}
    }
}
