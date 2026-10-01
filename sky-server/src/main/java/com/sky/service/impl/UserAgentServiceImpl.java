package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentHistoryMessage;
import com.sky.agent.model.AgentSubmitRequest;
import com.sky.agent.model.AgentKnowledgeScope;
import com.sky.agent.model.AgentTaskStatusResponse;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AgentBusinessException;
import com.sky.exception.AgentPermissionDeniedException;
import com.sky.exception.AgentTaskConflictException;
import com.sky.mapper.user.*;
import com.sky.properties.AgentProperties;
import com.sky.result.PageResult;
import com.sky.service.UserAgentService;
import com.sky.service.agent.UserAgentEventStreamCoordinator;
import com.sky.service.agent.UserAgentMessageCacheService;
import com.sky.service.agent.UserAgentSummaryService;
import com.sky.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.stream.Collectors;

/** 用户端Agent业务实现；所有业务读写均限定在user_agent_*表族。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAgentServiceImpl implements UserAgentService {
    private static final String DENIED = "资源不存在或无权访问";

    private final AgentClient agentClient;
    private final UserAgentSessionMapper sessionMapper;
    private final UserAgentTaskMapper taskMapper;
    private final UserAgentMessageMapper messageMapper;
    private final UserAgentEventMapper eventMapper;
    private final UserAgentSessionSummaryMapper summaryMapper;
    private final UserAgentCitationMapper citationMapper;
    private final AgentProperties properties;
    private final UserAgentEventStreamCoordinator coordinator;
    private final UserAgentMessageCacheService cacheService;
    private final UserAgentSummaryService summaryService;
    private final UserAgentKnowledgeMapper knowledgeMapper;

    @Override
    @Transactional
    public AgentSessionVO createSession(String title) {
        long userId = currentUserId();
        AgentSession session = AgentSession.builder()
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId).title(normalizeTitle(title)).status(1).messageCount(0).build();
        sessionMapper.insert(session);
        return sessionVO(session);
    }

    @Override
    @Transactional
    public AgentSubmitVO submitTask(AgentSubmitDTO dto) {
        long userId = currentUserId();
        String model = properties.getDefaultModel();
        AgentTask existing = taskMapper.getOwned(dto.getTaskId(), userId);
        if (existing != null) {
            String hash = requestHash(existing.getTaskId(), existing.getSessionId(), userId,
                    dto.getQuery(), model);
            if (existing.getRequestHash() != null && !existing.getRequestHash().equals(hash)) {
                throw new AgentTaskConflictException("taskId已被其他请求使用");
            }
            return submitVO(existing);
        }

        AgentSession session;
        String sessionId = dto.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            session = AgentSession.builder().sessionId(sessionId).userId(userId)
                    .title(normalizeTitle(dto.getQuery())).status(1).messageCount(0).build();
            sessionMapper.insert(session);
        } else {
            session = sessionMapper.getBySessionIdForUpdate(sessionId);
            if (session == null || !Long.valueOf(userId).equals(session.getUserId())) denied();
            if (session.getStatus() != null && session.getStatus() == 3) {
                throw new AgentBusinessException("会话已删除");
            }
        }

        String hash = requestHash(dto.getTaskId(), sessionId, userId, dto.getQuery(), model);
        AgentTask task = AgentTask.builder()
                .taskId(dto.getTaskId()).sessionId(sessionId).userId(userId)
                .query(dto.getQuery()).status(0).progress(0).model(model).requestHash(hash).build();
        if (taskMapper.insertIgnore(task) == 0) {
            AgentTask winner = taskMapper.getOwned(dto.getTaskId(), userId);
            if (winner == null) denied();
            if (winner.getRequestHash() != null && !winner.getRequestHash().equals(hash)) {
                throw new AgentTaskConflictException("taskId已被其他请求使用");
            }
            return submitVO(winner);
        }

        List<AgentHistoryMessage> history = buildHistory(sessionId);
        String traceId = MDC.get("trace_id");
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString().replace("-", "");
        AgentSubmitRequest request = new AgentSubmitRequest(
                task.getTaskId(), sessionId, userId, dto.getQuery(), model,
                properties.getDefaultTemperature(), context(history, dto.getClientContext()),
                buildPublicKnowledgeScope(), traceId, "USER", "CUSTOMER", "USER_ASSISTANT");
        try {
            agentClient.submit(request);
        } catch (RuntimeException exception) {
            try { agentClient.cancelTask(task.getTaskId(), "USER_ASSISTANT"); }
            catch (Exception ignored) { }
            throw new AgentBusinessException("Agent服务暂时不可用，请稍后重试");
        }

        int seqNo = messageMapper.getNextSeqNo(sessionId);
        messageMapper.insert(AgentMessage.builder()
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .sessionId(sessionId).taskId(task.getTaskId()).role(1)
                .content(dto.getQuery()).contentType("text").seqNo(seqNo).build());
        sessionMapper.incrementMessageCount(session.getId(), task.getTaskId());
        evictAfterCommit(session.getId());
        startAfterCommit(task.getTaskId());
        summarizeAfterCommit(sessionId);
        return submitVO(task);
    }

    /**
     * 根据服务端当前有效绑定生成只读知识范围；用户DTO和模型都无法指定kb_id。
     */
    private AgentKnowledgeScope buildPublicKnowledgeScope() {
        if (!properties.isUserAgentPublicRagEnabled()) return null;
        UserAgentKnowledgeBinding binding = knowledgeMapper.getActiveBinding("USER_CHAT");
        if (binding == null || binding.getReleaseId() == null) return null;
        UserAgentKnowledgeRelease release = knowledgeMapper.getRelease(binding.getReleaseId());
        if (release == null || !"PUBLISHED".equals(release.getStatus())) return null;
        LocalDateTime now = LocalDateTime.now();
        if ((release.getEffectiveFrom() != null && release.getEffectiveFrom().isAfter(now))
                || (release.getEffectiveUntil() != null && !release.getEffectiveUntil().isAfter(now))
                || knowledgeMapper.getPublishedBase(release.getKbId()) == null) {
            return null;
        }
        List<AgentKnowledgeDocument> documents = knowledgeMapper.listReleaseDocuments(binding.getReleaseId());
        Map<String, Integer> versions = documents.stream().collect(Collectors.toMap(
                AgentKnowledgeDocument::getDocumentId, AgentKnowledgeDocument::getVersion,
                (left, right) -> right));
        List<String> categories = documents.stream().map(AgentKnowledgeDocument::getCategory)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        OffsetDateTime expires = release.getEffectiveUntil() == null ? null
                : release.getEffectiveUntil().atOffset(ZoneOffset.UTC);
        return new AgentKnowledgeScope(release.getKbId(), versions, 8, 0.2,
                List.of(release.getReleaseId()), categories, expires);
    }

    @Override
    public PageResult pageQuerySessions(AgentSessionPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<AgentSession> page = sessionMapper.pageQuery(currentUserId(), dto.getStatus());
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public AgentSessionDetailVO getSessionDetail(String sessionId) {
        AgentSession session = ownedSession(sessionId);
        List<AgentMessage> messages = cacheService.get(session.getId());
        if (messages == null) {
            messages = messageMapper.listBySessionIdOrderBySeqNo(sessionId);
            cacheService.put(session.getId(), messages);
        }
        return AgentSessionDetailVO.builder().session(sessionVO(session))
                .messages(messages.stream().map(this::messageVO).toList()).build();
    }

    @Override
    @Transactional
    public void updateSession(AgentSessionUpdateDTO dto) {
        AgentSession session = ownedSession(dto.getSessionId());
        sessionMapper.update(AgentSession.builder().id(session.getId())
                .title(dto.getTitle()).status(dto.getStatus()).build());
        evictAfterCommit(session.getId());
    }

    @Override
    public PageResult pageQueryTasks(AgentTaskPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<AgentTask> page = taskMapper.pageQuery(
                dto.getSessionId(), currentUserId(), dto.getStatus());
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public AgentTaskVO getTaskDetail(String taskId) {
        AgentTaskVO vo = new AgentTaskVO();
        BeanUtils.copyProperties(ownedTask(taskId), vo);
        return vo;
    }

    @Override
    @Transactional
    public void cancelTask(String taskId) {
        AgentTask task = ownedTask(taskId);
        if (task.getStatus() == 4) return;
        if (task.getStatus() == 2 || task.getStatus() == 3) {
            throw new AgentBusinessException("任务已结束，无法取消");
        }
        AgentTaskStatusResponse response = agentClient.cancelTask(taskId, "USER_ASSISTANT");
        if (response == null || !"cancelled".equals(response.status())) {
            throw new AgentBusinessException("任务已经结束，无法取消");
        }
        taskMapper.transitionStatus(taskId, 4, task.getProgress(), null, null, List.of(0, 1));
    }

    @Override
    public List<AgentEvent> getEventsAfterSeqNo(String taskId, Integer lastSeqNo) {
        ownedTask(taskId);
        return eventMapper.listByTaskIdAfterSeqNo(taskId,
                lastSeqNo == null || lastSeqNo < 0 ? 0 : lastSeqNo);
    }

    private AgentSession ownedSession(String sessionId) {
        AgentSession session = sessionMapper.getOwned(sessionId, currentUserId());
        if (session == null) denied();
        return session;
    }

    private AgentTask ownedTask(String taskId) {
        AgentTask task = taskMapper.getOwned(taskId, currentUserId());
        if (task == null) denied();
        return task;
    }

    private long currentUserId() {
        Long id = BaseContext.getCurrentId();
        if (id == null || BaseContext.getCurrentRole() != null) denied();
        return id;
    }

    private void denied() {
        throw new AgentPermissionDeniedException(DENIED);
    }

    private List<AgentHistoryMessage> buildHistory(String sessionId) {
        LinkedList<AgentHistoryMessage> history = new LinkedList<>();
        AgentSessionSummary summary = summaryMapper.getBySessionId(sessionId);
        if (summary != null && summary.getSummary() != null && !summary.getSummary().isBlank()) {
            history.add(new AgentHistoryMessage("system", "用户会话历史摘要：\n" + summary.getSummary()));
        }
        List<AgentMessage> messages = messageMapper.listRecentBySessionId(
                sessionId, properties.getSummaryRecentMessageCount());
        int remaining = properties.getContextCharacterLimit();
        for (int index = messages.size() - 1; index >= 0 && remaining > 0; index--) {
            AgentMessage message = messages.get(index);
            if (message.getContent() == null || message.getContent().isBlank()) continue;
            String role = switch (message.getRole()) {
                case 1 -> "user"; case 2 -> "assistant"; case 3 -> "system"; default -> null;
            };
            if (role == null) continue;
            String content = message.getContent();
            if (content.length() > remaining) content = content.substring(content.length() - remaining);
            history.addFirst(new AgentHistoryMessage(role, content));
            remaining -= content.length();
        }
        return history;
    }

    private Map<String, Object> context(List<AgentHistoryMessage> history,
                                        Map<String, Object> clientContext) {
        return clientContext == null || clientContext.isEmpty()
                ? Map.of("history", history)
                : Map.of("history", history, "client_context", clientContext);
    }

    private AgentMessageVO messageVO(AgentMessage message) {
        AgentMessageVO vo = new AgentMessageVO();
        BeanUtils.copyProperties(message, vo);
        if (message.getRole() != null && message.getRole() == 2) {
            vo.setCitations(citationMapper.listByMessageId(message.getMessageId()).stream().map(value -> {
                AgentCitationVO citation = new AgentCitationVO();
                BeanUtils.copyProperties(value, citation);
                return citation;
            }).collect(Collectors.toList()));
        } else vo.setCitations(List.of());
        return vo;
    }

    private AgentSessionVO sessionVO(AgentSession session) {
        AgentSessionVO vo = new AgentSessionVO();
        BeanUtils.copyProperties(session, vo);
        return vo;
    }

    private AgentSubmitVO submitVO(AgentTask task) {
        return AgentSubmitVO.builder().taskId(task.getTaskId()).sessionId(task.getSessionId())
                .eventsUrl("/user/agent/tasks/" + task.getTaskId() + "/events")
                .status(task.getStatus()).assistantMessageId(task.getAssistantMessageId())
                .errorMsg(task.getErrorMsg()).build();
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank()) return "新对话";
        String title = value.trim();
        return title.length() > 30 ? title.substring(0, 30) + "..." : title;
    }

    private String requestHash(String taskId, String sessionId, long userId,
                               String query, String model) {
        return sha256(String.join("\u0000", taskId, sessionId, String.valueOf(userId), query, model));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private void startAfterCommit(String taskId) {
        afterCommit(() -> coordinator.start(taskId));
    }

    private void summarizeAfterCommit(String sessionId) {
        afterCommit(() -> summaryService.scheduleIfNeeded(sessionId));
    }

    private void evictAfterCommit(Long sessionDbId) {
        afterCommit(() -> cacheService.evict(sessionDbId));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
