package com.ai.assistant.mapper;

import com.ai.assistant.entity.KbDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    List<KbDocument> selectAll();

    List<KbDocument> selectByStatus(Integer status);
}
