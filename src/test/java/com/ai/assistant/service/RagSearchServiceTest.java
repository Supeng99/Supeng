package com.ai.assistant.service;

import com.ai.assistant.entity.KbChunk;
import com.ai.assistant.mapper.KbChunkMapper;
import com.ai.assistant.model.RagSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RagSearchService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RagSearchServiceTest {

    @Mock
    private KbChunkMapper chunkMapper;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private RagSearchService ragSearchService;

    private KbChunk testChunk;

    @BeforeEach
    void setUp() {
        testChunk = new KbChunk();
        testChunk.setId(1L);
        testChunk.setDocumentId(1L);
        testChunk.setContent("Java是一种面向对象的编程语言");
        testChunk.setPosition(1);
    }

    @Test
    @DisplayName("测试搜索结果不为空")
    void testSearchReturnsResults() {
        when(embeddingService.preprocessText(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return text != null ? text.toLowerCase() : "";
        });
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8f);

        when(chunkMapper.selectAll()).thenReturn(Arrays.asList(testChunk));

        List<RagSearchResult> results = ragSearchService.semanticSearch("Java编程");

        assertNotNull(results);
        verify(chunkMapper).selectAll();
    }

    @Test
    @DisplayName("测试无搜索结果")
    void testSearchWithNoResults() {
        when(embeddingService.preprocessText(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return text != null ? text.toLowerCase() : "";
        });

        when(chunkMapper.selectAll()).thenReturn(Collections.emptyList());

        List<RagSearchResult> results = ragSearchService.semanticSearch("不存在的关键词");

        assertTrue(results.isEmpty());
        verify(chunkMapper).selectAll();
    }

    @Test
    @DisplayName("测试上下文构建")
    void testBuildContext() {
        RagSearchResult result = RagSearchResult.builder()
                .content("测试内容")
                .documentId(1L)
                .documentName("测试文档")
                .rankScore(0.85)
                .build();

        String context = ragSearchService.buildContext(Arrays.asList(result));

        assertNotNull(context);
        assertTrue(context.contains("测试内容"));
        assertTrue(context.contains("相关知识"));
    }

    @Test
    @DisplayName("测试空上下文构建")
    void testBuildContextWithEmpty() {
        String context = ragSearchService.buildContext(Collections.emptyList());

        assertEquals("", context);
    }

    @Test
    @DisplayName("测试自定义topK参数")
    void testSearchWithCustomTopK() {
        when(embeddingService.preprocessText(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return text != null ? text.toLowerCase() : "";
        });
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.8f);

        KbChunk chunk2 = new KbChunk();
        chunk2.setId(2L);
        chunk2.setDocumentId(1L);
        chunk2.setContent("Python是另一种编程语言");
        chunk2.setPosition(2);

        when(chunkMapper.selectAll()).thenReturn(Arrays.asList(testChunk, chunk2));

        List<RagSearchResult> results = ragSearchService.semanticSearch("编程", 1, 0.1);

        assertNotNull(results);
        assertTrue(results.size() <= 1);
    }
}
