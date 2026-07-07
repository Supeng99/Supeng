package com.ai.assistant.mapper;

import com.ai.assistant.entity.KbChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    List<KbChunk> selectByDocumentId(@Param("documentId") Long documentId);

    List<KbChunk> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    List<KbChunk> selectAll();
}
