-- 智能AI问答助手系统数据库脚本

CREATE DATABASE IF NOT EXISTS ai_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_assistant;

-- 会话表
CREATE TABLE chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    title VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题',
    model_type VARCHAR(32) DEFAULT 'deepseek' COMMENT 'AI模型类型',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    message_count INT DEFAULT 0 COMMENT '消息数量',
    last_message TEXT COMMENT '最后一条消息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 消息表
CREATE TABLE chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(16) NOT NULL COMMENT '角色: user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    model_type VARCHAR(32) COMMENT '使用的AI模型',
    token_count INT DEFAULT 0 COMMENT 'Token数量',
    citations TEXT COMMENT '引用来源(JSON)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 知识库文档表
CREATE TABLE kb_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path VARCHAR(512) NOT NULL COMMENT '文件路径',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    file_type VARCHAR(16) NOT NULL COMMENT '文件类型: pdf/docx/doc/txt',
    chunk_count INT DEFAULT 0 COMMENT '分块数量',
    status TINYINT DEFAULT 0 COMMENT '处理状态: 0-待处理 1-处理中 2-完成 3-失败',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- 知识库分块表
CREATE TABLE kb_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分块ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    content TEXT NOT NULL COMMENT '分块内容',
    position INT DEFAULT 0 COMMENT '分块位置',
    token_count INT DEFAULT 0 COMMENT 'Token数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_document_id (document_id),
    INDEX idx_content (content(255)),
    FULLTEXT INDEX ft_content (content),
    FOREIGN KEY (document_id) REFERENCES kb_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分块表';
