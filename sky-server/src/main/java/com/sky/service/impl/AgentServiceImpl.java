package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentHistoryMessage;
import com.sky.agent.model.AgentSubmitRequest;
import com.sky.agent.model.AgentTaskStatusResponse;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AgentBusinessException;
import com.sky.mapper.*;
import com.sky.properties.AgentProperties;
import com.sky.result.PageResult;
import com.sky.service.AgentService;
import com.sky.service.agent.AgentEventStreamCoordinator;
import com.sky.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent智能体业务层实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentClient agentClient;
    private final AgentSessionMapper sessionMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentEventMapper eventMapper;
    private final AgentProperties agentProperties;
    private final AgentEventStreamCoordinator eventStreamCoordinator;

    @Override
    @Transactional
    public AgentSubmitVO submitTask(AgentSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String sessionId = dto.getSessionId();

        // 1. 会话处理：首轮创建新会话，后续轮次复用
        AgentSession session;
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            session = AgentSession.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .title(truncateTitle(dto.getQuery()))
                    .status(1)
                    .messageCount(0)
                    .build();
            sessionMapper.insert(session);
        } else {
            session = sessionMapper.getBySessionId(sessionId);
            if (session == null) {
                throw new AgentBusinessException("会话不存在: " + sessionId);
            }
            if (session.getStatus() == 3) {
                throw new AgentBusinessException("会话已删除: " + sessionId);
            }
            if (!userId.equals(session.getUserId())) {
                throw new AgentBusinessException("无权操作该会话");
            }
        }

        // 2. 调用Python Agent submit接口
        String taskId = UUID.randomUUID().toString();
        String model = dto.getModel() == null || dto.getModel().isBlank()
                ? agentProperties.getDefaultModel() : dto.getModel();
        List<AgentHistoryMessage> history = buildHistory(sessionId);
        AgentSubmitRequest request = new AgentSubmitRequest(
                taskId,
                sessionId,
                userId,
                dto.getQuery(),
                model,
                agentProperties.getDefaultTemperature(),
                Map.of("history", history));
        try {
            agentClient.submit(request);
        } catch (RuntimeException exception) {
            log.error("提交Python Agent任务失败, taskId={}", taskId, exception);
            try {
                agentClient.cancelTask(taskId);
            } catch (Exception ignored) {
                // Python可能尚未创建任务，此处仅做尽力清理。
            }
            throw new AgentBusinessException("Agent服务暂时不可用，请稍后重试");
        }

        // 3. 创建本地任务记录
        AgentTask task = AgentTask.builder()
                .taskId(taskId)
                .sessionId(sessionId)
                .userId(userId)
                .query(dto.getQuery())
                .status(0)
                .progress(0)
                .model(model)
                .build();
        taskMapper.insert(task);

        // 4. 保存用户消息
        sessionMapper.getBySessionIdForUpdate(sessionId);
        int userSeqNo = messageMapper.getNextSeqNo(sessionId);
        AgentMessage userMessage = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .sessionId(sessionId)
                .taskId(taskId)
                .role(1)
                .content(dto.getQuery())
                .contentType("text")
                .seqNo(userSeqNo)
                .build();
        messageMapper.insert(userMessage);

        // 5. 更新会话的最后任务ID和消息数
        sessionMapper.incrementMessageCount(session.getId(), taskId);
        startEventSubscriptionAfterCommit(taskId);

        return AgentSubmitVO.builder()
                .taskId(taskId)
                .sessionId(sessionId)
                .eventsUrl("/admin/agent/tasks/" + taskId + "/events")
                .status(0)
                .build();
    }

    @Override
    public PageResult pageQuerySessions(AgentSessionPageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<AgentSession> page = sessionMapper.pageQuery(userId, dto.getStatus());
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public AgentSessionDetailVO getSessionDetail(String sessionId) {
        Long userId = BaseContext.getCurrentId();
        AgentSession session = sessionMapper.getBySessionId(sessionId);
        if (session == null) {
            throw new AgentBusinessException("会话不存在: " + sessionId);
        }
        if (!userId.equals(session.getUserId())) {
            throw new AgentBusinessException("无权访问该会话");
        }

        AgentSessionVO vo = new AgentSessionVO();
        BeanUtils.copyProperties(session, vo);

        List<AgentMessage> messages = messageMapper.listBySessionIdOrderBySeqNo(sessionId);
        List<AgentMessageVO> messageVOs = messages.stream()
                .map(this::convertMessageVO)
                .collect(Collectors.toList());

        return AgentSessionDetailVO.builder()
                .session(vo)
                .messages(messageVOs)
                .build();
    }

    @Override
    @Transactional
    public void updateSession(AgentSessionUpdateDTO dto) {
        AgentSession session = sessionMapper.getBySessionId(dto.getSessionId());
        if (session == null) {
            throw new AgentBusinessException("会话不存在: " + dto.getSessionId());
        }
        Long userId = BaseContext.getCurrentId();
        if (!userId.equals(session.getUserId())) {
            throw new AgentBusinessException("无权操作该会话");
        }

        AgentSession update = AgentSession.builder()
                .id(session.getId())
                .title(dto.getTitle())
                .status(dto.getStatus())
                .build();
        sessionMapper.update(update);
    }

    @Override
    public PageResult pageQueryTasks(AgentTaskPageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<AgentTask> page = taskMapper.pageQuery(dto.getSessionId(), userId, dto.getStatus());
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public AgentTaskVO getTaskDetail(String taskId) {
        AgentTask task = getOwnedTask(taskId);
        AgentTaskVO vo = new AgentTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    @Override
    @Transactional
    public void cancelTask(String taskId) {
        AgentTask task = getOwnedTask(taskId);
        if (task.getStatus() == 4) {
            return;
        }
        if (task.getStatus() == 2 || task.getStatus() == 3) {
            throw new AgentBusinessException("任务已结束，无法取消");
        }
        AgentTaskStatusResponse pythonStatus;
        try {
            pythonStatus = agentClient.cancelTask(taskId);
        } catch (RuntimeException exception) {
            log.error("取消Python Agent任务失败, taskId={}", taskId, exception);
            throw new AgentBusinessException("Agent任务取消失败，请稍后重试");
        }
        if (pythonStatus == null || !"cancelled".equals(pythonStatus.status())) {
            throw new AgentBusinessException("任务已经结束，无法取消");
        }
        taskMapper.transitionStatus(taskId, 4, task.getProgress(), null,
                null, List.of(0, 1));
    }

    @Override
    public List<AgentEvent> getEventsAfterSeqNo(String taskId, Integer lastSeqNo) {
        getOwnedTask(taskId);
        if (lastSeqNo == null || lastSeqNo < 0) {
            lastSeqNo = 0;
        }
        return eventMapper.listByTaskIdAfterSeqNo(taskId, lastSeqNo);
    }

    // ---- 私有方法 ----

    private AgentMessageVO convertMessageVO(AgentMessage msg) {
        AgentMessageVO vo = new AgentMessageVO();
        BeanUtils.copyProperties(msg, vo);
        return vo;
    }

    private AgentTask getOwnedTask(String taskId) {
        Long userId = BaseContext.getCurrentId();
        AgentTask task = taskMapper.getByTaskIdAndUserId(taskId, userId);
        if (task == null) {
            throw new AgentBusinessException("任务不存在或无权访问");
        }
        return task;
    }

    private List<AgentHistoryMessage> buildHistory(String sessionId) {
        List<AgentMessage> messages = messageMapper.listRecentBySessionId(
                sessionId, agentProperties.getContextMessageLimit());
        int remainingCharacters = agentProperties.getContextCharacterLimit();
        java.util.LinkedList<AgentHistoryMessage> history = new java.util.LinkedList<>();
        for (int index = messages.size() - 1; index >= 0 && remainingCharacters > 0; index--) {
            AgentMessage message = messages.get(index);
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = switch (message.getRole()) {
                case 1 -> "user";
                case 2 -> "assistant";
                case 3 -> "system";
                default -> null;
            };
            if (role == null) {
                continue;
            }
            String content = message.getContent();
            if (content.length() > remainingCharacters) {
                content = content.substring(content.length() - remainingCharacters);
            }
            history.addFirst(new AgentHistoryMessage(role, content));
            remainingCharacters -= content.length();
        }
        return history;
    }

    private void startEventSubscriptionAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventStreamCoordinator.start(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventStreamCoordinator.start(taskId);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        agentClient.cancelTask(taskId);
                    } catch (Exception exception) {
                        log.warn("本地事务回滚后取消Python任务失败, taskId={}", taskId, exception);
                    }
                }
            }
        });
    }

    private String truncateTitle(String query) {
        if (query == null) {
            return "新对话";
        }
        return query.length() > 30 ? query.substring(0, 30) + "..." : query;
    }
}
