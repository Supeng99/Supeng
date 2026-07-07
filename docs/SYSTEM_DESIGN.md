# 系统设计文档

## 一、系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                           前端 (Vue/React)                           │
│                    WebSocket / SSE 长连接                             │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Nginx (负载均衡)                              │
│                      - 动静分离                                       │
│                      - SSL终结                                        │
│                      - 限流防刷                                       │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Spring Boot 应用集群                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │  实例1       │  │  实例2       │  │  实例N       │                  │
│  └─────────────┘  └─────────────┘  └─────────────┘                  │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Controller层                                                 │  │
│  │  - ChatController (聊天)                                     │  │
│  │  - SessionController (会话管理)                               │  │
│  │  - DocumentController (文档管理)                              │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Service层                                                    │  │
│  │  - AiModelFactory (策略模式: DeepSeek/Doubao/Qwen)            │  │
│  │  - RagSearchService (知识库检索)                              │  │
│  │  - TokenBucketRateLimiter (令牌桶限流)                        │  │
│  │  - MultiLevelCacheService (多级缓存)                          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  DAO层 (MyBatis Plus)                                        │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
          │                    │                    │
          ▼                    ▼                    ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│     MySQL       │   │     Redis       │   │     MinIO       │
│  - 会话消息      │   │  - 会话缓存      │   │  - 文档存储      │
│  - 知识库       │   │  - 限流计数      │   │  - 静态资源      │
│  - 用户配置      │   │  - Token存储    │   │                  │
└─────────────────┘   └─────────────────┘   └─────────────────┘
```

---

## 二、核心模块设计

### 2.1 AI多模型工厂 (策略模式)

```java
// 策略接口
public interface AiModelClient {
    String chat(List<Message> messages);
    SseEmitter chatStream(List<Message> messages, SseEmitter emitter);
}

// 具体策略
public class DeepSeekClient implements AiModelClient { ... }
public class DoubaoClient implements AiModelClient { ... }
public class QwenClient implements AiModelClient { ... }

// 工厂选择
public class AiModelFactory {
    public AiModelClient getClient(String modelType) {
        return clientMap.getOrDefault(modelType, deepSeekClient);
    }
}
```

**设计优势**:
- 新增模型只需实现 `AiModelClient` 接口
- 配置化切换，无需修改代码
- 便于灰度发布和AB测试

### 2.2 RAG知识库检索流程

```
用户提问
    │
    ▼
┌─────────────────────────────────────┐
│  1. 文本预处理 (分词/去停用词)        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 向量化 (Embedding Service)       │
│     - 调用嵌入模型                    │
│     - 生成384维向量                  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 向量检索 (Milvus/MySQL全文索引)   │
│     - 余弦相似度计算                  │
│     - Top-K 召回                    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 关键词匹配 (BM25算法)            │
│     - 精确关键词匹配                  │
│     - 位置权重加成                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 分数融合                        │
│     finalScore = vec_sim * 0.7      │
│               + keyword * 0.3      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 上下文构建                      │
│     - 组装prompt                    │
│     - 返回Top-N相关片段              │
└─────────────────────────────────────┘
    │
    ▼
AI模型生成回答
```

### 2.3 SSE流式输出架构

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(ChatRequest request) {
    SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30分钟超时

    CompletableFuture.runAsync(() -> {
        try {
            aiClient.chatStream(messages, emitter);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    emitter.onCompletion(() -> log.info("SSE连接完成"));
    emitter.onTimeout(() -> log.warn("SSE连接超时"));
    emitter.onError(e -> log.error("SSE错误", e));

    return emitter;
}
```

---

## 三、数据库设计

### 3.1 ER图

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│chat_session  │       │chat_message  │       │   kb_document │
├──────────────┤       ├──────────────┤       ├──────────────┤
│id            │──┐    │id            │       │id            │
│user_id       │  │    │session_id    │◄──────│file_name     │
│title         │  │    │role          │       │file_path     │
│model_type    │  │    │content       │       │status        │
│status        │  │    │token_count   │       │chunk_count   │
│message_count │  │    │create_time   │       │create_time   │
│create_time   │  │    │update_time   │       │update_time   │
│update_time   │  │    └──────────────┘       └──────────────┘
└──────────────┘  │                                   │
                  │                                   │
                  └──────────┬────────────────────────┘
                             │
                             ▼
                    ┌──────────────┐
                    │   kb_chunk   │
                    ├──────────────┤
                    │id            │
                    │document_id   │◄──────── kb_document
                    │content       │
                    │position      │
                    │token_count   │
                    │vector        │ (预留向量字段)
                    │create_time   │
                    └──────────────┘
```

### 3.2 核心表结构

```sql
-- 会话表
CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    title VARCHAR(255) DEFAULT '新对话',
    model_type VARCHAR(32) DEFAULT 'deepseek',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    message_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL COMMENT 'user/assistant/system',
    content TEXT NOT NULL,
    token_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_create (create_time),
    FOREIGN KEY (session_id) REFERENCES chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库文档表
CREATE TABLE kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT COMMENT '文件大小(字节)',
    file_type VARCHAR(32) COMMENT 'pdf/docx/txt',
    status TINYINT DEFAULT 0 COMMENT '0-待处理, 1-成功, -1-失败',
    chunk_count INT DEFAULT 0,
    error_message VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_create (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库chunk表
CREATE TABLE kb_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    position INT COMMENT '块位置',
    token_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_document (document_id),
    FULLTEXT INDEX idx_content (content),
    FOREIGN KEY (document_id) REFERENCES kb_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 四、扩展性设计

### 4.1 水平扩展方案

```
                    ┌─────────────────┐
                    │  Nginx Cluster  │
                    │   (LVS + VIP)   │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│  Spring Boot  │    │  Spring Boot  │    │  Spring Boot  │
│    实例1       │    │    实例2       │    │    实例N       │
│  (2核4G)      │    │  (2核4G)      │    │  (2核4G)      │
└───────────────┘    └───────────────┘    └───────────────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│    MySQL       │    │    Redis      │    │    MinIO      │
│   主从复制      │    │   集群模式     │    │   分布式存储   │
└───────────────┘    └───────────────┘    └───────────────┘
```

### 4.2 微服务拆分方案 (未来演进)

| 服务 | 职责 | 扩展策略 |
|------|------|----------|
| api-gateway | 路由、限流、鉴权 | Nacos + Sentinel |
| chat-service | 聊天业务逻辑 | 独立部署，水平扩展 |
| rag-service | 知识库检索 | GPU服务器，按需扩展 |
| ai-proxy | AI模型代理 | 多地域部署，降低延迟 |
| file-service | 文件上传下载 | MinIO集群 |

---

## 五、安全设计

### 5.1 接口安全

```java
// 1. 请求签名验证
@Aspect
public class SignAspect {
    @Around("@annotation(Signed)")
    public Object verifySign(ProceedingJoinPoint point) {
        String sign = request.getHeader("X-Sign");
        String timestamp = request.getHeader("X-Timestamp");

        // 验证签名 + 时间戳防重放
        if (!verify(sign, timestamp)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}

// 2. 敏感信息脱敏
@Aspect
@Component
public class MaskAspect {
    @AfterReturning(pointcut = "execution(* ..*Controller.*(..))", returning = "result")
    public void maskSensitive(Object result) {
        // 脱敏手机号、身份证等
    }
}
```

### 5.2 AI输出安全

```java
// 敏感词过滤
@Component
public class ContentFilter {

    private final Set<String> sensitiveWords = new HashSet<>();

    public String filter(String content) {
        for (String word : sensitiveWords) {
            content = content.replaceAll(word, "***");
        }
        return content;
    }
}
```

---

## 六、面试核心要点

### 6.1 技术选型理由

| 技术 | 选型理由 | 面试话术 |
|------|----------|----------|
| SSE vs WebSocket | SSE单向，更轻量 | "聊天场景SSE足够，简单易用，服务端推送更高效" |
| Redis vs 本地缓存 | 分布式部署需要 | "多实例部署，本地缓存无法共享，Redis保证一致性" |
| MySQL vs MongoDB | 事务+结构化查询 | "聊天记录需要事务支持，MySQL更合适" |

### 6.2 架构设计原则

1. **单一职责**: 每个类/模块只做一件事
2. **开闭原则**: 对扩展开放，对修改关闭
3. **依赖倒置**: 面向接口编程
4. **里氏替换**: 子类可替换父类

### 6.3 常见问题回答

**Q: 如何保证消息顺序？**
> "消息表用自增ID保证绝对顺序，时间戳作为辅助。AI回复因为是异步的，可能乱序，但前端会根据ID重新排序。"

**Q: 多轮对话如何实现？**
> "每次请求带上sessionId，后端从数据库加载历史消息，构建完整的对话上下文传给AI模型。"

**Q: SSE断了怎么办？**
> "前端实现重连机制：检测到断开后，等待2秒重试，最多3次。后端设置30分钟超时，释放资源。"
