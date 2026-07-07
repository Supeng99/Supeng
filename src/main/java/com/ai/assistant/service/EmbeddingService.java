package com.ai.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量嵌入服务（模拟版）
 * 生产环境应替换为真实的向量数据库（如Milvus、Pinecone）
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final int VECTOR_DIMENSION = 384;

    /**
     * 将文本转换为向量（模拟实现）
     * 生产环境应调用 OpenAI Embedding API 或本地模型
     */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[VECTOR_DIMENSION];
        }

        float[] vector = new float[VECTOR_DIMENSION];

        String[] words = text.toLowerCase().split("\\s+");
        Random random = new Random(Objects.hash(text));

        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            float sum = 0;
            for (String word : words) {
                sum += hashWord(word) * (random.nextFloat() - 0.5f);
            }
            vector[i] = (float) Math.tanh(sum / Math.max(1, words.length));
        }

        normalize(vector);
        return vector;
    }

    /**
     * 计算余弦相似度
     */
    public float cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) {
            return 0f;
        }

        float dotProduct = 0f;
        float norm1 = 0f;
        float norm2 = 0f;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        float denominator = (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
        return denominator == 0 ? 0f : dotProduct / denominator;
    }

    /**
     * 批量生成向量
     */
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream()
                .map(this::embed)
                .collect(Collectors.toList());
    }

    /**
     * 向量归一化
     */
    private void normalize(float[] vector) {
        float norm = 0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * 字符串哈希
     */
    private int hashWord(String str) {
        int h = 0;
        for (char c : str.toCharArray()) {
            h = 31 * h + c;
        }
        return h;
    }

    /**
     * 预处理文本
     */
    public String preprocessText(String text) {
        if (text == null) return "";

        return text.toLowerCase()
                .replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
