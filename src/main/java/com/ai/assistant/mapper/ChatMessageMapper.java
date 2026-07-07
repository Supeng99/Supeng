package com.ai.assistant.mapper;

import com.ai.assistant.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    List<ChatMessage> selectBySessionId(@Param("sessionId") Long sessionId);

    List<ChatMessage> selectPageBySessionId(@Param("sessionId") Long sessionId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countBySessionId(@Param("sessionId") Long sessionId);
}
