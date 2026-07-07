package com.ai.assistant.controller;

import com.ai.assistant.entity.KbChunk;
import com.ai.assistant.entity.KbDocument;
import com.ai.assistant.model.ApiResponse;
import com.ai.assistant.service.DocumentProcessService;
import com.ai.assistant.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentProcessService documentProcessService;

    @PostMapping("/upload")
    public ApiResponse<KbDocument> upload(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Uploading file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());
            KbDocument document = documentService.uploadDocument(file);
            return ApiResponse.success(document);
        } catch (IllegalArgumentException e) {
            log.warn("Upload validation failed: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ApiResponse.error("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<KbDocument>> listDocuments() {
        try {
            List<KbDocument> documents = documentService.getAllDocuments();
            return ApiResponse.success(documents);
        } catch (Exception e) {
            log.error("Failed to list documents", e);
            return ApiResponse.error("Failed to list documents: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<KbDocument> getDocument(@PathVariable Long id) {
        try {
            KbDocument document = documentService.getDocument(id);
            if (document == null) {
                return ApiResponse.error("Document not found");
            }
            return ApiResponse.success(document);
        } catch (Exception e) {
            log.error("Failed to get document {}", id, e);
            return ApiResponse.error("Failed to get document: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/chunks")
    public ApiResponse<List<KbChunk>> getDocumentChunks(@PathVariable Long id) {
        try {
            List<KbChunk> chunks = documentService.getChunksByDocumentId(id);
            return ApiResponse.success(chunks);
        } catch (Exception e) {
            log.error("Failed to get chunks for document {}", id, e);
            return ApiResponse.error("Failed to get chunks: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
        try {
            documentService.deleteDocument(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Failed to delete document {}", id, e);
            return ApiResponse.error("Failed to delete document: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/reprocess")
    public ApiResponse<Void> reprocessDocument(@PathVariable Long id) {
        try {
            documentService.reprocessDocument(id);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to reprocess document {}", id, e);
            return ApiResponse.error("Failed to reprocess document: " + e.getMessage());
        }
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<KbDocument>> getDocumentsByStatus(@PathVariable Integer status) {
        try {
            List<KbDocument> documents = documentService.getDocumentsByStatus(status);
            return ApiResponse.success(documents);
        } catch (Exception e) {
            log.error("Failed to get documents by status {}", status, e);
            return ApiResponse.error("Failed to get documents: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam String keyword) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return ApiResponse.error("Keyword cannot be empty");
            }
            List<KbChunk> chunks = documentService.searchChunksByKeyword(keyword);
            List<Map<String, Object>> results = new java.util.ArrayList<>();
            for (KbChunk chunk : chunks) {
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("content", chunk.getContent());
                item.put("fileName", documentService.getDocument(chunk.getDocumentId()) != null 
                    ? documentService.getDocument(chunk.getDocumentId()).getFileName() : "未知文档");
                results.add(item);
            }
            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("Search failed for keyword: {}", keyword, e);
            return ApiResponse.error("Search failed: " + e.getMessage());
        }
    }

    // ==================== 文档智能处理 API ====================

    /**
     * 生成文档摘要
     */
    @GetMapping("/{id}/summary")
    public ApiResponse<String> generateSummary(@PathVariable Long id) {
        try {
            String summary = documentProcessService.generateSummary(id);
            return ApiResponse.success(summary);
        } catch (Exception e) {
            log.error("Failed to generate summary for document {}", id, e);
            return ApiResponse.error("生成摘要失败: " + e.getMessage());
        }
    }

    /**
     * 文档智能问答
     */
    @PostMapping("/{id}/question")
    public ApiResponse<String> questionAboutDocument(@PathVariable Long id, @RequestParam String question) {
        try {
            if (question == null || question.trim().isEmpty()) {
                return ApiResponse.error("问题不能为空");
            }
            String answer = documentProcessService.questionAboutDocument(id, question);
            return ApiResponse.success(answer);
        } catch (Exception e) {
            log.error("Failed to answer question for document {}", id, e);
            return ApiResponse.error("问答处理失败: " + e.getMessage());
        }
    }

    /**
     * 文档内容改写润色
     */
    @PostMapping("/{id}/rewrite")
    public ApiResponse<String> rewriteDocument(@PathVariable Long id, @RequestParam(defaultValue = "default") String style) {
        try {
            String result = documentProcessService.rewriteContent(id, style);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to rewrite document {}", id, e);
            return ApiResponse.error("改写失败: " + e.getMessage());
        }
    }

    /**
     * 文档翻译
     */
    @PostMapping("/{id}/translate")
    public ApiResponse<String> translateDocument(@PathVariable Long id, @RequestParam(defaultValue = "en") String targetLang) {
        try {
            String result = documentProcessService.translateDocument(id, targetLang);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to translate document {}", id, e);
            return ApiResponse.error("翻译失败: " + e.getMessage());
        }
    }

    /**
     * 文档扩写
     */
    @PostMapping("/{id}/expand")
    public ApiResponse<String> expandDocument(@PathVariable Long id, @RequestParam(defaultValue = "") String focusArea) {
        try {
            String result = documentProcessService.expandContent(id, focusArea);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to expand document {}", id, e);
            return ApiResponse.error("扩写失败: " + e.getMessage());
        }
    }

    /**
     * 提取关键信息
     */
    @PostMapping("/{id}/keyinfo")
    public ApiResponse<Map<String, Object>> extractKeyInfo(@PathVariable Long id) {
        try {
            Map<String, Object> result = documentProcessService.extractKeyInfo(id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to extract key info from document {}", id, e);
            return ApiResponse.error("提取关键信息失败: " + e.getMessage());
        }
    }

    /**
     * 文档续写
     */
    @PostMapping("/{id}/continue")
    public ApiResponse<String> continueWriting(@PathVariable Long id) {
        try {
            String result = documentProcessService.continueWriting(id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to continue writing document {}", id, e);
            return ApiResponse.error("续写失败: " + e.getMessage());
        }
    }

    /**
     * 对比两个文档
     */
    @PostMapping("/compare")
    public ApiResponse<String> compareDocuments(@RequestParam Long doc1Id, @RequestParam Long doc2Id) {
        try {
            if (doc1Id == null || doc2Id == null) {
                return ApiResponse.error("需要提供两个文档ID");
            }
            String result = documentProcessService.compareDocuments(doc1Id, doc2Id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to compare documents", e);
            return ApiResponse.error("文档对比失败: " + e.getMessage());
        }
    }
}
