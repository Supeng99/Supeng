package com.ai.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingService 单元测试
 */
class EmbeddingServiceTest {

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService();
    }

    @Test
    @DisplayName("测试向量生成：相同文本应生成相同向量")
    void testEmbedSameText() {
        String text = "Hello World";
        float[] vec1 = embeddingService.embed(text);
        float[] vec2 = embeddingService.embed(text);

        assertNotNull(vec1);
        assertEquals(384, vec1.length);

        assertArrayEquals(vec1, vec2);
    }

    @Test
    @DisplayName("测试向量生成：不同文本应生成不同向量")
    void testEmbedDifferentText() {
        float[] vec1 = embeddingService.embed("Hello");
        float[] vec2 = embeddingService.embed("World");

        assertNotNull(vec1);
        assertNotNull(vec2);
        assertFalse(Arrays.equals(vec1, vec2));
    }

    @Test
    @DisplayName("测试余弦相似度：相同向量相似度为1")
    void testCosineSimilarityIdentical() {
        float[] vec = embeddingService.embed("test");
        float similarity = embeddingService.cosineSimilarity(vec, vec);

        assertEquals(1.0f, similarity, 0.0001f);
    }

    @Test
    @DisplayName("测试余弦相似度：不同向量相似度小于1")
    void testCosineSimilarityDifferent() {
        float[] vec1 = embeddingService.embed("machine learning");
        float[] vec2 = embeddingService.embed("cooking recipes");

        float similarity = embeddingService.cosineSimilarity(vec1, vec2);

        assertTrue(similarity >= 0 && similarity <= 1);
        assertTrue(similarity < 1.0f);
    }

    @Test
    @DisplayName("测试空文本处理")
    void testEmptyText() {
        float[] vec = embeddingService.embed("");
        assertNotNull(vec);
        assertEquals(384, vec.length);
    }

    @Test
    @DisplayName("测试null处理")
    void testNullText() {
        float[] vec = embeddingService.embed(null);
        assertNotNull(vec);
        assertEquals(384, vec.length);
    }

    @Test
    @DisplayName("测试批量生成向量")
    void testEmbedBatch() {
        List<String> texts = Arrays.asList("Hello", "World", "Test");

        List<float[]> vectors = embeddingService.embedBatch(texts);

        assertNotNull(vectors);
        assertEquals(3, vectors.size());
        for (float[] vec : vectors) {
            assertEquals(384, vec.length);
        }
    }

    @Test
    @DisplayName("测试文本预处理")
    void testPreprocessText() {
        String result = embeddingService.preprocessText("Hello, World! 123");
        assertEquals("hello world 123", result);
    }

    @Test
    @DisplayName("测试语义相似度计算")
    void testSemanticSimilarity() {
        float[] vec1 = embeddingService.embed("artificial intelligence");
        float[] vec2 = embeddingService.embed("machine learning");

        float similarity = embeddingService.cosineSimilarity(vec1, vec2);

        assertFalse(Float.isNaN(similarity), "相似度不应为NaN");
        assertTrue(similarity >= -1f && similarity <= 1f, "相似度应在有效范围内");
    }
}
