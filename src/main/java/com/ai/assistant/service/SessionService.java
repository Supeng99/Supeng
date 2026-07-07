package com.ai.assistant.service;

import com.ai.assistant.entity.ChatMessage;
import com.ai.assistant.entity.ChatSession;
import com.ai.assistant.mapper.ChatMessageMapper;
import com.ai.assistant.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    public ChatSession createSession(String userId, String modelType) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("New Chat");
        session.setModelType(modelType);
        session.setStatus(1);
        session.setMessageCount(0);
        sessionMapper.insert(session);
        log.info("Created new session {} for user {}", session.getId(), userId);
        return session;
    }

    public ChatSession getSession(Long id) {
        return sessionMapper.selectById(id);
    }

    public List<ChatSession> getUserSessions(String userId) {
        return sessionMapper.selectByUserId(userId);
    }

    public PageInfo<ChatSession> getUserSessionsPaged(String userId, int page, int size) {
        PageHelper.startPage(page, size);
        List<ChatSession> sessions = sessionMapper.selectByUserId(userId);
        return new PageInfo<>(sessions);
    }

    public PageInfo<ChatSession> getSessionsPaged(String userId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<ChatSession> sessions = sessionMapper.selectPageByUserId(userId, offset, pageSize);

        long total = sessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
        );

        PageInfo<ChatSession> pageInfo = new PageInfo<>();
        pageInfo.setList(sessions);
        pageInfo.setTotal(total);
        pageInfo.setPageNum(pageNum);
        pageInfo.setPageSize(pageSize);
        pageInfo.setPages((int) Math.ceil((double) total / pageSize));

        return pageInfo;
    }

    @Transactional
    public ChatSession updateSession(Long id, String title) {
        ChatSession session = sessionMapper.selectById(id);
        if (session != null) {
            if (title != null && !title.trim().isEmpty()) {
                session.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
            }
            sessionMapper.updateById(session);
        }
        return session;
    }

    @Transactional
    public void deleteSession(Long id) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, id);
        messageMapper.delete(wrapper);
        sessionMapper.deleteById(id);
        log.info("Deleted session {} and its messages", id);
    }

    @Transactional
    public void deleteUserSessions(String userId) {
        List<ChatSession> sessions = sessionMapper.selectByUserId(userId);
        List<Long> sessionIds = sessions.stream()
                .map(ChatSession::getId)
                .collect(Collectors.toList());

        if (!sessionIds.isEmpty()) {
            LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.in(ChatMessage::getSessionId, sessionIds);
            messageMapper.delete(msgWrapper);
        }

        LambdaQueryWrapper<ChatSession> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.eq(ChatSession::getUserId, userId);
        sessionMapper.delete(sessionWrapper);

        log.info("Deleted all sessions for user {}", userId);
    }

    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    public PageInfo<ChatMessage> getSessionMessagesPaged(Long sessionId, int page, int size) {
        int offset = (page - 1) * size;

        List<ChatMessage> messages = messageMapper.selectPageBySessionId(sessionId, offset, size);
        int total = messageMapper.countBySessionId(sessionId);

        PageInfo<ChatMessage> pageInfo = new PageInfo<>();
        pageInfo.setList(messages);
        pageInfo.setTotal(total);
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(size);
        pageInfo.setPages((int) Math.ceil((double) total / size));

        return pageInfo;
    }

    @Transactional
    public void archiveSession(Long id) {
        ChatSession session = sessionMapper.selectById(id);
        if (session != null) {
            session.setStatus(0);
            sessionMapper.updateById(session);
            log.info("Archived session {}", id);
        }
    }

    @Transactional
    public void unarchiveSession(Long id) {
        ChatSession session = sessionMapper.selectById(id);
        if (session != null) {
            session.setStatus(1);
            sessionMapper.updateById(session);
            log.info("Unarchived session {}", id);
        }
    }

    public long getUserSessionCount(String userId) {
        return sessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
        );
    }

    @Transactional
    public void updateSessionModel(Long id, String modelType) {
        ChatSession session = sessionMapper.selectById(id);
        if (session != null) {
            session.setModelType(modelType);
            sessionMapper.updateById(session);
            log.info("Updated session {} model to {}", id, modelType);
        }
    }
}
