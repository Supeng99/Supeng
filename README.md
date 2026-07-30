# 智能AI问答助手系统

企业级智能问答助手，支持多AI模型、RAG知识库、SSE流式响应。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.x (JDK 8+) |
| ORM | MyBatis Plus 3.5.x |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis |
| AI模型 | DeepSeek  / 通义千问 |
| 流式输出 | SSE (Server-Sent Events) |
| 文档处理 | Apache PDFBox + POI |

## 核心功能

- **多AI模型切换**: DeepSeek、通义千问，策略模式热插拔
- **SSE流式响应**: 实时打字机效果，用户体验流畅
- **RAG知识库**: PDF/Word/TXT文档上传、智能分块、全文检索
- **Redis限流**: 用户/IP/全局三级令牌桶限流
- **多轮对话**: 基于会话的上下文记忆，连贯问答

## 快速部署

### 1. 环境要求

- JDK 8+
- Maven 3.6+
- MySQL 8.0
- Redis

### 2. 数据库初始化

```sql
mysql -u root -p < src/main/resources/schema.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_assistant
    username: your_username
    password: your_password
  redis:
    host: your_redis_host
    port: 6379

ai:
  deepseek:
    api-key: your_deepseek_api_key
  doubao:
    api-key: your_doubao_api_key
  qwen:
    api-key: your_qwen_api_key
```

### 4. 编译运行

```bash
mvn clean package -DskipTests
java -jar target/ai-assistant-1.0.0.jar
```

### 5. 访问

浏览器打开: http://localhost:8080/

## API接口

### 对话接口

**流式对话 (SSE)**
```
POST /api/chat/stream
Content-Type: application/json

{
  "sessionId": 1,
  "message": "你好",
  "modelType": "deepseek",
  "searchKnowledge": true
}
```

**非流式对话**
```
POST /api/chat/non-stream
Content-Type: application/json

{
  "sessionId": 1,
  "message": "你好",
  "modelType": "deepseek"
}
```

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/session/create | 创建会话 |
| GET | /api/session/{id} | 获取会话详情 |
| GET | /api/session/list | 会话列表 |
| GET | /api/session/{id}/messages | 消息历史 |
| PUT | /api/session/{id} | 更新会话标题 |
| DELETE | /api/session/{id} | 删除会话 |

### 文档管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/document/upload | 上传文档 |
| GET | /api/document/list | 文档列表 |
| GET | /api/document/{id} | 文档详情 |
| GET | /api/document/{id}/chunks | 文档分块 |
| GET | /api/document/search | 知识库检索 |
| POST | /api/document/{id}/reprocess | 重新处理 |
| DELETE | /api/document/{id} | 删除文档 |

## 项目结构

```
src/main/java/com/ai/assistant/
├── AiAssistantApplication.java       # 启动类
├── entity/                           # 实体类
│   ├── ChatSession.java              # 会话实体
│   ├── ChatMessage.java              # 消息实体
│   ├── KbDocument.java               # 知识库文档实体
│   └── KbChunk.java                  # 知识库分块实体
├── mapper/                           # MyBatis Plus Mapper
│   ├── ChatSessionMapper.java
│   ├── ChatMessageMapper.java
│   ├── KbDocumentMapper.java
│   └── KbChunkMapper.java
├── service/                          # 业务服务层
│   ├── AiModelClient.java            # AI客户端接口
│   ├── AiModelFactory.java           # AI模型工厂
│   ├── DeepSeekClient.java           # DeepSeek客户端
│   ├── DoubaoClient.java             # 豆包客户端
│   ├── QwenClient.java              # 通义千问客户端
│   ├── ChatService.java             # 对话服务
│   ├── SessionService.java          # 会话管理服务
│   ├── DocumentService.java         # 文档服务
│   └── RateLimitService.java        # 限流服务
├── controller/                       # REST API控制器
│   ├── ChatController.java
│   ├── SessionController.java
│   └── DocumentController.java
├── config/                           # 配置类
│   ├── AiConfig.java                # AI模型配置
│   ├── RedisConfig.java             # Redis配置
│   ├── CacheConfig.java             # 缓存配置
│   ├── RateLimitConfig.java        # 限流配置
│   ├── UploadConfig.java            # 上传配置
│   ├── WebMvcConfig.java           # Web MVC配置
│   └── MetaObjectHandlerImpl.java   # 自动填充处理器
├── model/                           # 请求/响应模型
│   ├── ApiResponse.java             # 统一响应
│   ├── ChatRequest.java             # 聊天请求
│   ├── ChatResponse.java           # 聊天响应
│   └── AiMessage.java              # AI消息
└── aop/                             # 切面
    └── RateLimitInterceptor.java    # 限流拦截器
```

## 数据库表结构

```
chat_session      - 会话表
chat_message     - 消息表
kb_document      - 知识库文档表
kb_chunk         - 知识库分块表
```

详见 `src/main/resources/schema.sql`

## 简历核心亮点

1. **SSE流式响应**: 后端使用SSE技术推送，前端实现打字机效果，用户体验流畅
2. **RAG知识库**: 完整实现文档上传→智能分块→全文检索→AI回答流程
3. **Redis限流**: 多维度令牌桶限流，防止API恶意调用
4. **多模型热插拔**: 策略模式设计，切换AI模型无需修改业务代码
5. **多轮对话**: 基于会话的上下文管理，AI能记忆对话历史
