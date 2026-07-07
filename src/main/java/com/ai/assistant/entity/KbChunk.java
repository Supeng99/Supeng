package com.ai.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("kb_chunk")
public class KbChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private String content;

    private Integer position;

    private Integer tokenCount;

    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
