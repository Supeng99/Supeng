package com.ai.assistant.service;

import com.ai.assistant.entity.KbChunk;
import com.ai.assistant.entity.KbDocument;
import com.ai.assistant.mapper.KbChunkMapper;
import com.ai.assistant.mapper.KbDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DocumentProcessService {

    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final AiModelFactory aiModelFactory;

    @Autowired
    public DocumentProcessService(KbChunkMapper chunkMapper, KbDocumentMapper documentMapper, AiModelFactory aiModelFactory) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.aiModelFactory = aiModelFactory;
    }

    /**
     * 调用AI模型处理
     */
    private String processWithAI(String prompt) {
        try {
            AiModelClient client = aiModelFactory.getClient("deepseek");
            List<com.ai.assistant.model.AiMessage> messages = new java.util.ArrayList<>();
            messages.add(new com.ai.assistant.model.AiMessage("user", prompt));
            return client.chat(messages);
        } catch (Exception e) {
            log.error("AI processing failed", e);
            return "处理失败: " + e.getMessage();
        }
    }

    /**
     * 生成文档摘要
     */
    public String generateSummary(Long documentId) {
        List<KbChunk> chunks = getChunksByDocumentId(documentId);
        if (chunks.isEmpty()) {
            return "文档内容为空，无法生成摘要";
        }
        
        StringBuilder content = new StringBuilder();
        for (KbChunk chunk : chunks) {
            content.append(chunk.getContent()).append("\n\n");
        }
        
        String prompt = "请为以下文档生成简洁的摘要（150字以内）：\n\n" + content.toString();
        return processWithAI(prompt);
    }

    /**
     * 文档智能问答
     */
    public String questionAboutDocument(Long documentId, String question) {
        List<KbChunk> chunks = getChunksByDocumentId(documentId);
        if (chunks.isEmpty()) {
            return "文档内容为空，无法回答问题";
        }
        
        StringBuilder content = new StringBuilder();
        for (KbChunk chunk : chunks) {
            content.append(chunk.getContent()).append("\n\n");
        }
        
        String prompt = "根据以下文档内容回答问题。如果文档中没有相关信息，请说明'文档中没有提供相关信息'。\n\n【文档内容】\n" 
            + content.toString() + "\n\n【问题】" + question;
        return processWithAI(prompt);
    }

    /**
     * 文档内容改写润色
     */
    public String rewriteContent(Long documentId, String style) {
        List<KbChunk> chunks = getChunksByDocumentId(documentId);
        if (chunks.isEmpty()) {
            return "文档内容为空，无法改写";
        }
        
        StringBuilder content = new StringBuilder();
        for (KbChunk chunk : chunks) {
            content.append(chunk.getContent()).append("\n\n");
        }
        
        String stylePrompt = "";
        switch (style) {
            case "formal":
                stylePrompt = "请将以下内容改写为正式、专业的商务风格：";
                break;
            case "simple":
                stylePrompt = "请将以下内容改写为简洁、易懂的口语化风格：";
                break;
            case "academic":
                stylePrompt = "请将以下内容改写为学术论文风格：";
                break;
            default:
                stylePrompt = "请对以下内容进行润色改写：";
        }
        
        String prompt = stylePrompt + "\n\n" + content.toString();
        return processWithAI(prompt);
    }

    /**
     * 文档翻译
     */
    public String translateDocument(Long documentId, String targetLang) {
        List<KbChunk> chunks = getChunksByDocumentId(documentId);
        if (chunks.isEmpty()) {
            return "文档内容为空，无法翻译";
        }
        
        StringBuilder content = new StringBuilder();
        for (KbChunk chunk : chunks) {
            content.append(chunk.getContent()).append("\n\n");
        }
        
        String langName = "中文";
        switch (targetLang) {
            case "en": langName = "英文"; break;
            case "ja": langName = "日文"; break;
            case "ko": langName = "韩文"; break;
            case "fr": langName = "法文"; break;
            case "de": langName = "德文"; break;
        }
        
        String prompt = "请将以下内容翻译成" + langName + "，保持原文的格式和语气：\n\n" + content.toString();
        return processWithAI(prompt);
    }

    /**
     * 文档内容扩写
     */
    public String expandContent(Long documentId, String focusArea) {
        List<KbChunk> chunks = getChunksByDocumentId(documentId);
        if (chunks.isEmpty()) {
            return "文档内容为空，无法扩写";
        }
        
        StringBuilder content = new StringBuilder();
        for (KbChunk chunk : chunks) {
            content.append(chunk.getContent()).append("\n\n");
        }
        
        String prompt = "请根据以下文档内容，围绕'" + focusArea + "'这个主题进行详细扩写，补充相关背景、例子和解释：\n\n"
            + "【原文】\n" + content.toString();
        return processWithAI(prompt);
    }

    /**
     * 提取文档关键信息
     */
    public Map<String, Object> extractKeyInfo(Long documentId) {
        Map<String, Object> result = new HashMap<>();
        List<KbChunk> chunks = getChunksByDocumentId(documentId);
        KbDocument doc = documentMapper.selectById(documentId);
        
        if (chunks.isEmpty()) {
            return result;
        }
        
        result.put("fileName", doc != null ? doc.getFileName() : "未知");
        result.put("fileType", doc != null ? doc.getFileType() : "未知");
        result.put("chunkCount", chunks.size());
        
        StringBuilder content = new StringBuilder();
        for (KbChunk chunk : chunks) {
            content.append(chunk.getContent()).append("\n\n");
        }
        
        String prompt = "请从以下文档中提取关键信息，以JSON格式返回，包含：\n"
            + "1. 核心主题 (mainTopic)\n"
            + "2. 关键要点列表 (keyPoints) - 数组格式\n"
            + "3. 涉及的重要概念 (concepts) - 数组格式\n"
            + "4. 文档类型/领域 (domain)\n\n"
            + "只返回JSON，不要其他文字：\n\n" + content.toString();
        
        String extracted = processWithAI(prompt);
        result.put("extracted", extracted);
        
        return result;
    }

    /**
     * 文档内容续写
     */
    public String continueWriting(Long documentId) {
        List<KbChunk> chunks = getChunksByDocumentId(documentId);
        if (chunks.isEmpty()) {
            return "文档内容为空，无法续写";
        }
        
        StringBuilder content = new StringBuilder();
        for (KbChunk chunk : chunks) {
            content.append(chunk.getContent()).append("\n\n");
        }
        
        String prompt = "请根据以下文档的结尾或整体内容，续写接下来的内容，保持相同的风格和逻辑连贯性：\n\n"
            + content.toString();
        return processWithAI(prompt);
    }

    /**
     * 对比两个文档的内容差异
     */
    public String compareDocuments(Long doc1Id, Long doc2Id) {
        List<KbChunk> chunks1 = getChunksByDocumentId(doc1Id);
        List<KbChunk> chunks2 = getChunksByDocumentId(doc2Id);
        
        if (chunks1.isEmpty() || chunks2.isEmpty()) {
            return "其中一个文档内容为空，无法对比";
        }
        
        StringBuilder content1 = new StringBuilder();
        for (KbChunk chunk : chunks1) {
            content1.append(chunk.getContent()).append("\n");
        }
        
        StringBuilder content2 = new StringBuilder();
        for (KbChunk chunk : chunks2) {
            content2.append(chunk.getContent()).append("\n");
        }
        
        KbDocument doc1 = documentMapper.selectById(doc1Id);
        KbDocument doc2 = documentMapper.selectById(doc2Id);
        
        String prompt = "请对比以下两份文档的内容差异，从主题、观点、细节等维度进行分析：\n\n"
            + "【文档1: " + (doc1 != null ? doc1.getFileName() : "未知") + "】\n" + content1.toString() + "\n\n"
            + "【文档2: " + (doc2 != null ? doc2.getFileName() : "未知") + "】\n" + content2.toString();
        return processWithAI(prompt);
    }

    private List<KbChunk> getChunksByDocumentId(Long documentId) {
        LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbChunk::getDocumentId, documentId);
        wrapper.orderByAsc(KbChunk::getPosition);
        return chunkMapper.selectList(wrapper);
    }
}
