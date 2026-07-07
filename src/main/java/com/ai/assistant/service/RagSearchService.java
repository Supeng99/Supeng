package com.ai.assistant.service;

import com.ai.assistant.entity.KbChunk;
import com.ai.assistant.mapper.KbChunkMapper;
import com.ai.assistant.model.RagSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG语义搜索服务
 * 结合向量检索 + 关键词匹配 + 相关性评分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private final KbChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;

    private static final int DEFAULT_TOP_K = 5;
    private static final double MIN_RELEVANCE_SCORE = 0.3;
    private static final double KEYWORD_BOOST = 0.2;

    /**
     * 语义搜索知识库
     *
     * @param query       查询文本
     * @param topK        返回数量
     * @param minScore    最低相关性分数
     * @return 排序后的搜索结果
     */
    public List<RagSearchResult> semanticSearch(String query, int topK, double minScore) {
        long startTime = System.currentTimeMillis();

        String processedQuery = embeddingService.preprocessText(query);
        float[] queryVector = embeddingService.embed(processedQuery);

        Set<String> queryKeywords = extractKeywords(processedQuery);

        List<KbChunk> chunks = chunkMapper.selectAll();

        List<RagSearchResult> results = new ArrayList<>();

        for (KbChunk chunk : chunks) {
            float[] chunkVector = embeddingService.embed(
                    embeddingService.preprocessText(chunk.getContent()));

            float similarity = embeddingService.cosineSimilarity(queryVector, chunkVector);

            double relevanceScore = calculateRelevanceScore(chunk.getContent(), queryKeywords);

            double finalScore = similarity * 0.7 + relevanceScore * 0.3;

            if (finalScore >= minScore) {
                RagSearchResult result = RagSearchResult.builder()
                        .content(chunk.getContent())
                        .documentId(chunk.getDocumentId())
                        .position(chunk.getPosition())
                        .similarityScore((double) similarity)
                        .relevanceScore(relevanceScore)
                        .matchedKeywords(findMatchedKeywords(chunk.getContent(), queryKeywords))
                        .source("knowledge_base")
                        .build();
                result.calculateRankScore();
                results.add(result);
            }
        }

        List<RagSearchResult> sortedResults = results.stream()
                .sorted(Comparator.comparingDouble(RagSearchResult::getRankScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("RAG搜索完成 - query: {}, candidates: {}, results: {}, cost: {}ms",
                truncateQuery(query), chunks.size(), sortedResults.size(),
                System.currentTimeMillis() - startTime);

        return sortedResults;
    }

    /**
     * 简化版搜索
     */
    public List<RagSearchResult> semanticSearch(String query) {
        return semanticSearch(query, DEFAULT_TOP_K, MIN_RELEVANCE_SCORE);
    }

    /**
     * 计算相关性分数
     */
    private double calculateRelevanceScore(String content, Set<String> keywords) {
        if (keywords.isEmpty()) return 0.5;

        String lowerContent = content.toLowerCase();
        int matchCount = 0;

        for (String keyword : keywords) {
            if (lowerContent.contains(keyword)) {
                matchCount++;
            }
        }

        double baseScore = (double) matchCount / keywords.size();

        int position = lowerContent.indexOf(keywords.stream().findFirst().orElse(""));
        double positionBoost = position >= 0 && position < 50 ? 0.1 : 0;

        return Math.min(1.0, baseScore + positionBoost);
    }

    /**
     * 提取关键词
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();

        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }

        List<String> stopWords = Arrays.asList(
                "的", "了", "是", "在", "我", "有", "和", "就", "不", "人",
                "the", "a", "an", "is", "are", "was", "were", "be", "been", "being"
        );

        keywords.removeAll(stopWords);

        return keywords;
    }

    /**
     * 查找匹配的关键词
     */
    private List<String> findMatchedKeywords(String content, Set<String> queryKeywords) {
        String lowerContent = content.toLowerCase();
        return queryKeywords.stream()
                .filter(keyword -> lowerContent.contains(keyword))
                .collect(Collectors.toList());
    }

    /**
     * 构建RAG上下文
     */
    public String buildContext(List<RagSearchResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("【相关知识】\n\n");

        for (int i = 0; i < results.size(); i++) {
            RagSearchResult result = results.get(i);
            context.append(String.format("[来源%d] %s (相关度: %.2f%%)\n%s\n\n",
                    i + 1,
                    result.getDocumentName() != null ? result.getDocumentName() : "文档" + result.getDocumentId(),
                    result.getRankScore() * 100,
                    result.getContent()));
        }

        return context.toString();
    }

    /**
     * 截断查询文本用于日志
     */
    private String truncateQuery(String query) {
        if (query == null) return "";
        return query.length() > 50 ? query.substring(0, 50) + "..." : query;
    }
}
