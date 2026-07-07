# 智能AI问答助手系统 - 项目规格说明书

## 1. 项目概述

### 1.1 项目名称
智能AI问答助手系统（Enterprise AI Q&A Assistant System）

### 1.2 项目版本
- 版本号: 1.0.0

### 1.3 核心用途
- 企业内部智能问答
- 文档智能解析与知识库构建
- 智能客服系统
- 业务知识问答

## 2. 技术架构

| 层级 | 技术选型 |
|------|---------|
| 后端框架 | Spring Boot 2.7.x |
| JDK版本 | JDK 8+ |
| ORM | MyBatis Plus 3.5.x |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis |
| AI对接 | DeepSeek / 豆包 / 通义千问 |
| 流式输出 | SSE (Server-Sent Events) |
| 文档处理 | Apache PDFBox + POI |
| 限流 | Redis + 令牌桶算法 |
| 分页 | PageHelper |

## 3. 功能模块

### 3.1 AI 多轮对话
- 支持用户与AI进行多轮连续对话
- AI能够记忆上下文，实现连贯问答
- 支持切换不同AI模型
- 会话持久化存储

### 3.2 SSE 流式响应
- 后端流式推送，实时输出AI回答
- 前端实现打字机效果
- 支持中断和错误处理

### 3.3 RAG 智能知识库
- 支持 PDF、Word (docx)、TXT 文档上传
- 文档内容智能解析与文本拆分
- 基于关键词的全文检索
- AI 基于知识库内容回答问题

### 3.4 限流防刷
- 基于Redis的令牌桶限流
- 三级限流: 用户维度 / IP维度 / 全局维度
- 防止AI接口被恶意刷调用

### 3.5 对话管理
- 会话列表管理
- 历史消息分页查询
- 会话归档与恢复
- 会话标题自动生成

## 4. 数据库设计

### 4.1 表结构

| 表名 | 说明 | 主键 |
|------|------|------|
| chat_session | 会话表 | id |
| chat_message | 消息表 | id |
| kb_document | 知识库文档表 | id |
| kb_chunk | 知识库分块表 | id |

详见 `src/main/resources/schema.sql`

### 4.2 索引设计
- user_id 索引 - 用户会话查询
- session_id 索引 - 消息关联查询
- document_id 索引 - 文档分块关联
- FULLTEXT 索引 - 知识库全文检索

## 5. API接口规范

### 5.1 对话接口
- `POST /api/chat/stream` - SSE流式对话
- `POST /api/chat/non-stream` - 非流式对话
- `GET /api/chat/health` - 健康检查

### 5.2 会话管理接口
- `POST /api/session/create` - 创建会话
- `GET /api/session/{id}` - 获取会话
- `GET /api/session/list` - 会话列表
- `GET /api/session/page` - 会话分页
- `PUT /api/session/{id}` - 更新会话
- `DELETE /api/session/{id}` - 删除会话
- `GET /api/session/{id}/messages` - 消息历史
- `POST /api/session/{id}/archive` - 归档会话

### 5.3 文档管理接口
- `POST /api/document/upload` - 上传文档
- `GET /api/document/list` - 文档列表
- `GET /api/document/{id}` - 文档详情
- `GET /api/document/{id}/chunks` - 文档分块
- `GET /api/document/search` - 知识库检索
- `POST /api/document/{id}/reprocess` - 重新处理
- `DELETE /api/document/{id}` - 删除文档

## 6. 设计模式

### 6.1 策略模式
- `AiModelClient` 接口定义AI客户端规范
- `DeepSeekClient`、`DoubaoClient`、`QwenClient` 实现该接口
- `AiModelFactory` 工厂类根据类型获取对应客户端

### 6.2 责任链模式
- `RateLimitInterceptor` 限流拦截器
- 多维度限流检查

## 7. 简历核心亮点

| 亮点 | 说明 |
|------|------|
| SSE流式响应 | 实现Server-Sent Events实时流式响应，前端打字机效果，用户体验流畅 |
| RAG知识库 | 文档上传→智能分块→全文检索→AI回答完整流程，支持PDF/Word/TXT |
| Redis限流 | 多维度令牌桶限流（用户/IP/全局），防止API恶意调用 |
| 多模型切换 | DeepSeek/豆包/通义千问策略模式热插拔，切换模型无需修改代码 |
| 多轮对话 | 基于会话的上下文管理，AI能记忆对话历史 |

## 8. 项目文件清单

```
src/main/java/com/ai/assistant/
├── AiAssistantApplication.java       # Spring Boot启动类
├── entity/                          # 实体层 (4个)
├── mapper/                          # MyBatis Mapper层 (4个)
├── service/                         # 服务层 (8个)
├── controller/                      # 控制器层 (3个)
├── config/                          # 配置层 (8个)
├── model/                           # 模型层 (4个)
└── aop/                             # 切面层 (1个)

src/main/resources/
├── application.yml                  # 应用配置
├── schema.sql                      # 数据库脚本
├── mapper/                         # MyBatis XML映射文件
└── static/                         # 静态资源
    └── index.html                  # 前端页面
```
