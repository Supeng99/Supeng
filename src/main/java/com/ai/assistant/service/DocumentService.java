package com.ai.assistant.service;

import com.ai.assistant.config.UploadConfig;
import com.ai.assistant.entity.KbChunk;
import com.ai.assistant.entity.KbDocument;
import com.ai.assistant.mapper.KbChunkMapper;
import com.ai.assistant.mapper.KbDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;
    private static final int MIN_CHUNK_LENGTH = 50;

    private final UploadConfig uploadConfig;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;

    @Transactional
    public KbDocument uploadDocument(MultipartFile file) {
        validateFile(file);

        try {
            String originalFilename = file.getOriginalFilename();
            String fileType = getFileType(originalFilename);
            String savedFileName = UUID.randomUUID().toString() + "." + fileType;

            Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(savedFileName);
            log.info("Uploading file to: {}", filePath.toAbsolutePath());
            file.transferTo(filePath.toFile());

            KbDocument document = new KbDocument();
            document.setFileName(originalFilename);
            document.setFilePath(filePath.toString());
            document.setFileSize(file.getSize());
            document.setFileType(fileType);
            document.setStatus(0);
            documentMapper.insert(document);

            processDocument(document, filePath);

            return document;
        } catch (IOException e) {
            log.error("Failed to upload document", e);
            throw new RuntimeException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String fileType = getFileType(originalFilename).toLowerCase();
        List<String> allowedTypes = uploadConfig.getAllowedTypes().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        if (!allowedTypes.contains(fileType)) {
            throw new IllegalArgumentException("File type not allowed: " + fileType);
        }
    }

    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    @Transactional
    public void processDocument(KbDocument document, Path filePath) {
        try {
            String content = extractText(filePath.toFile(), document.getFileType());

            List<String> chunks = splitTextSmart(content, document.getFileName());

            for (int i = 0; i < chunks.size(); i++) {
                KbChunk chunk = new KbChunk();
                chunk.setDocumentId(document.getId());
                chunk.setContent(chunks.get(i));
                chunk.setPosition(i + 1);
                chunk.setTokenCount(estimateTokenCount(chunks.get(i)));
                chunkMapper.insert(chunk);
            }

            document.setChunkCount(chunks.size());
            document.setStatus(1);
            documentMapper.updateById(document);

            log.info("Processed document {} with {} chunks", document.getId(), chunks.size());
        } catch (Exception e) {
            log.error("Failed to process document {}", document.getId(), e);
            document.setStatus(-1);
            document.setErrorMessage(e.getMessage());
            documentMapper.updateById(document);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    private String extractText(File file, String fileType) throws IOException {
        String extension = fileType.toLowerCase();

        switch (extension) {
            case "pdf":
                return extractPdfText(file);
            case "docx":
                return extractDocxText(file);
            case "doc":
                return extractDocText(file);
            case "txt":
                return extractTxtText(file);
            case "java":
            case "py":
            case "js":
            case "ts":
            case "cpp":
            case "c":
            case "html":
            case "css":
            case "sql":
            case "xml":
            case "json":
            case "yaml":
            case "yml":
                return extractCodeText(file);
            default:
                throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
    }

    private String extractPdfText(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocxText(File file) throws IOException {
        try (InputStream is = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDocText(File file) {
        throw new UnsupportedOperationException("DOC format requires additional library (Apache POI OLE2)");
    }

    private String extractTxtText(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes);
    }

    private String extractCodeText(File file) throws IOException {
        return extractTxtText(file);
    }

    private List<String> splitTextSmart(String content, String fileName) {
        List<String> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }

        String extension = fileName != null ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase() : "";

        if (isCodeFile(extension)) {
            return splitCodeContent(content, extension);
        } else {
            return splitTextContent(content);
        }
    }

    private boolean isCodeFile(String extension) {
        List<String> codeExtensions = Arrays.asList(
            "java", "py", "js", "ts", "jsx", "tsx", "cpp", "c", "h", "hpp",
            "cs", "go", "rs", "swift", "kt", "scala", "php", "rb", "html",
            "css", "scss", "less", "sql", "xml", "json", "yaml", "yml",
            "md", "txt", "sh", "bash", "ps1", "vue", "jsx"
        );
        return codeExtensions.contains(extension.toLowerCase());
    }

    private List<String> splitCodeContent(String content, String extension) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;

        Pattern classPattern = Pattern.compile("(class|interface|enum|def|function|func|public|private|protected)\\s+\\w+");
        Pattern methodPattern = Pattern.compile("(public|private|protected)?\\s*(static)?\\s*\\w+\\s*\\([^)]*\\)\\s*[{:]");
        Pattern commentPattern = Pattern.compile("^\\s*(//|#|/\\*|\\*|<!--)");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isClassStart = classPattern.matcher(line).find();
            boolean isMethodStart = methodPattern.matcher(line).find();
            boolean isComment = commentPattern.matcher(line).find();

            if (isClassStart && currentLength > MIN_CHUNK_LENGTH) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                currentLength = 0;
            } else if (isMethodStart && currentLength > 200) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                currentLength = 0;
            }

            currentChunk.append(line).append("\n");
            currentLength += line.length();

            if (currentLength >= CHUNK_SIZE) {
                String chunk = currentChunk.toString().trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                currentChunk = new StringBuilder();
                currentLength = 0;

                if (i > 0 && i < lines.length - 1) {
                    for (int backtrack = Math.min(5, i); backtrack > 0; backtrack--) {
                        String prevLine = lines[i - backtrack];
                        if (methodPattern.matcher(prevLine).find() || classPattern.matcher(prevLine).find()) {
                            currentChunk.append(prevLine).append("\n");
                            currentLength += prevLine.length();
                            break;
                        }
                    }
                }
            }
        }

        if (currentChunk.length() > MIN_CHUNK_LENGTH) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private List<String> splitTextContent(String content) {
        List<String> chunks = new ArrayList<>();

        Pattern paragraphPattern = Pattern.compile("[。！？.!?\n]{2,}");
        String[] paragraphs = paragraphPattern.split(content);

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            if (currentChunk.length() + paragraph.length() <= CHUNK_SIZE) {
                currentChunk.append(paragraph).append("\n\n");
            } else {
                if (currentChunk.length() > MIN_CHUNK_LENGTH) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder(paragraph).append("\n\n");
            }
        }

        if (currentChunk.length() > MIN_CHUNK_LENGTH) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private int estimateTokenCount(String text) {
        return (int) Math.ceil((text != null ? text.length() : 0) / 4.0);
    }

    public String searchKnowledge(String keyword) {
        List<KbChunk> chunks = searchChunksByKeyword(keyword);
        return chunks.stream()
                .map(KbChunk::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    public List<KbChunk> searchChunksByKeyword(String keyword) {
        return chunkMapper.searchByKeyword(keyword, 5);
    }

    public List<KbChunk> searchChunksByKeywordWithLimit(String keyword, int limit) {
        return chunkMapper.searchByKeyword(keyword, limit);
    }

    public List<KbDocument> getAllDocuments() {
        return documentMapper.selectAll();
    }

    public List<KbDocument> getDocumentsByStatus(Integer status) {
        return documentMapper.selectByStatus(status);
    }

    public KbDocument getDocument(Long id) {
        return documentMapper.selectById(id);
    }

    public List<KbChunk> getChunksByDocumentId(Long documentId) {
        return chunkMapper.selectByDocumentId(documentId);
    }

    @Transactional
    public void deleteDocument(Long id) {
        KbDocument document = documentMapper.selectById(id);
        if (document != null) {
            LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(KbChunk::getDocumentId, id);
            chunkMapper.delete(wrapper);

            try {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete file: {}", document.getFilePath(), e);
            }

            documentMapper.deleteById(id);
        }
    }

    @Transactional
    public void reprocessDocument(Long id) {
        KbDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + id);
        }

        LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbChunk::getDocumentId, id);
        chunkMapper.delete(wrapper);

        document.setStatus(0);
        document.setChunkCount(0);
        document.setErrorMessage(null);
        documentMapper.updateById(document);

        try {
            processDocument(document, Paths.get(document.getFilePath()));
        } catch (Exception e) {
            log.error("Failed to reprocess document {}", id, e);
            throw new RuntimeException("Failed to reprocess document: " + e.getMessage(), e);
        }
    }
}
