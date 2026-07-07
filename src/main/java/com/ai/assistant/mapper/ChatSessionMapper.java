package com.ai.assistant.mapper;

import com.ai.assistant.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    List<ChatSession> selectByUserId(@Param("userId") String userId);

    List<ChatSession> selectPageByUserId(@Param("userId") String userId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);
}
