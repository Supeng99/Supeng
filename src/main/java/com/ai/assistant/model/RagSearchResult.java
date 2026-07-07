package com.ai.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchResult {

    /**
     * 知识库chunk内容
     */
    private String content;

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文档名称
     */
    private String documentName;

    /**
     * 块位置
     */
    private Integer position;

    /**
     * 相关性分数 (0-1)
     */
    private Double relevanceScore;

    /**
     * 相似度分数 (0-1)
     */
    private Double similarityScore;

    /**
     * 关键词匹配列表
     */
    private List<String> matchedKeywords;

    /**
     * 来源标识
     */
    private String source;

    /**
     * 综合排名分数
     */
    private Double rankScore;

    /**
     * 计算最终排名分数
     */
    public void calculateRankScore() {
        this.rankScore = (this.similarityScore != null ? this.similarityScore : 0.0) * 0.7
                + (this.relevanceScore != null ? this.relevanceScore : 0.0) * 0.3;
    }
}
