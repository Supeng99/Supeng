-- 修复数据库表结构，将 BLOB 改为 TEXT 类型
USE ai_assistant;

ALTER TABLE chat_message MODIFY COLUMN content TEXT NOT NULL COMMENT '消息内容';
ALTER TABLE chat_message MODIFY COLUMN citations TEXT COMMENT '引用来源';

ALTER TABLE chat_session MODIFY COLUMN title VARCHAR(255) DEFAULT NULL COMMENT '会话标题';
ALTER TABLE chat_session MODIFY COLUMN last_message TEXT COMMENT '最后一条消息';
