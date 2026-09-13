package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentHistoryMessage;
import com.sky.agent.model.AgentKnowledgeScope;
import com.sky.agent.model.AgentSubmitRequest;
import com.sky.agent.AgentClientException;
import com.sky.agent.model.AgentTaskStatusResponse;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AgentBusinessException;
import com.sky.exception.AgentTaskConflictException;
import com.sky.enumeration.AdminRole;
import com.sky.mapper.*;
import com.sky.mapper.AgentSessionSummaryMapper;
import com.sky.properties.AgentProperties;
import com.sky.result.PageResult;
import com.sky.service.AgentService;
import com.sky.service.agent.AgentEventStreamCoordinator;
import com.sky.service.agent.AgentMessageCacheService;
import com.sky.service.agent.AgentSummaryService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;
import org.slf4j.MDC;

/**
 * Agent智能体业务层实现类
 * <p>
 * 负责处理所有与智能助手核心功能相关的业务逻辑，
 * 包括任务提交、会话管理、消息历史构建、事件流处理以及与Python Agent服务的交互。
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
    private final AgentMessageCacheService messageCacheService;
    private final AgentSummaryService summaryService;
    private final AgentKnowledgeMapper knowledgeMapper;
    private final AgentCitationMapper citationMapper;

    /**
     * 提交一个新的智能助手任务。
     * <p>
     * 此方法实现了复杂的幂等性逻辑，以处理重试和并发请求：
     * 1.  如果提供了`taskId`且任务已存在，会进行哈希校验，确保请求内容未变，然后返回现有任务信息。
     * 2.  如果`sessionId`为空，会创建一个新的会话。
     * 3.  使用`taskMapper.insertIgnore`原子性地插入任务记录，防止同一`taskId`被重复创建。
     * 4.  成功抢占`taskId`后，调用Python Agent服务执行任务。
     * 5.  在事务提交后，启动事件流订阅、清理消息缓存并调度会话摘要。
     *
     * @param dto 包含任务详情的数据传输对象 (AgentSubmitDTO)
     * @return 包含任务ID、会话ID和事件URL的提交结果 (AgentSubmitVO)
     * @throws AgentTaskConflictException 如果`taskId`已被用于不同的请求
     * @throws AgentBusinessException     如果会话不存在或用户无权操作
     */
    @Override
    @Transactional
    public AgentSubmitVO submitTask(AgentSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String actorRole = AdminRole.fromDatabase(BaseContext.getCurrentRole()).name();
        String requestedTaskId = dto.getTaskId();
        String requestedKbId = normalizeKbId(dto.getKbId());
        String model = dto.getModel() == null || dto.getModel().isBlank()
                ? agentProperties.getDefaultModel() : dto.getModel();

        // 重试检查：如果任务已存在，验证请求一致性，避免重复处理
        AgentTask existing = taskMapper.getByTaskIdAndUserId(requestedTaskId, userId);
        if (existing != null) {
            if (dto.getSessionId() != null && !dto.getSessionId().isBlank()
                    && !dto.getSessionId().equals(existing.getSessionId())) {
                throw new AgentTaskConflictException("taskId已被其他会话使用");
            }
            AgentSession existingSession = sessionMapper.getBySessionId(existing.getSessionId());
            String effectiveKbId = requestedKbId == null && existingSession != null
                    ? existingSession.getKbId() : requestedKbId;
            if (requestedKbId != null && existingSession != null
                    && !sameNullable(requestedKbId, existingSession.getKbId())) {
                throw new AgentTaskConflictException("taskId已被其他知识库请求使用");
            }
            String requestHash = requestHash(requestedTaskId, existing.getSessionId(), userId,
                    dto.getQuery(), model, effectiveKbId);
            if (!matchesStoredRequestHash(existing.getRequestHash(), requestHash, requestedTaskId,
                    existing.getSessionId(), userId, dto.getQuery(), model, requestedKbId == null)) {
                throw new AgentTaskConflictException("taskId已被其他请求使用");
            }
            return toSubmitVO(existing);
        }
        String sessionId = dto.getSessionId();

        // 1. 会话处理：首轮创建新会话，后续轮次复用
        AgentSession session;
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            validateKnowledgeBase(requestedKbId, userId);
            session = AgentSession.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .kbId(requestedKbId)
                    .title(truncateTitle(dto.getQuery()))
                    .status(1) // 1: active
                    .messageCount(0)
                    .build();
            sessionMapper.insert(session);
        } else {
            session = sessionMapper.getBySessionIdForUpdate(sessionId);
            if (session == null) {
                throw new AgentBusinessException("会话不存在: " + sessionId);
            }
            if (session.getStatus() == 3) { // 3: deleted
                throw new AgentBusinessException("会话已删除: " + sessionId);
            }
            if (!userId.equals(session.getUserId())) {
                throw new AgentBusinessException("无权操作该会话");
            }
            bindKnowledgeBase(session, requestedKbId, userId);
        }

        // 2. 任务抢占：使用数据库唯一约束原子性地插入任务，只有成功者才能继续
        String taskId = requestedTaskId;
        List<AgentHistoryMessage> history = buildHistory(sessionId, session.getId());
        String requestHash = requestHash(taskId, sessionId, userId, dto.getQuery(), model, session.getKbId());
        AgentTask task = AgentTask.builder()
                .taskId(taskId)
                .sessionId(sessionId)
                .userId(userId)
                .actorRole(actorRole)
                .query(dto.getQuery())
                .status(0) // 0: created
                .progress(0)
                .model(model)
                .requestHash(requestHash)
                .build();
        if (taskMapper.insertIgnore(task) == 0) {
            // 插入失败，意味着任务已存在，进行冲突检查
            AgentTask winner = taskMapper.getByTaskIdAndUserId(taskId, userId);
            if (winner == null) {
                throw new AgentBusinessException("任务已存在或无权访问");
            }
            if (winner.getRequestHash() != null && !winner.getRequestHash().equals(requestHash)) {
                throw new AgentTaskConflictException("taskId已被其他请求使用");
            }
            return toSubmitVO(winner);
        }

        // 3. 调用Python Agent服务，实际执行任务
        String traceId = MDC.get("trace_id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        AgentSubmitRequest request = new AgentSubmitRequest(
                taskId,
                sessionId,
                userId,
                dto.getQuery(),
                model,
                agentProperties.getDefaultTemperature(),
                Map.of("history", history), buildKnowledgeScope(session.getKbId(), userId), traceId,
                actorRole);
        log.info("提交Python Agent调用, traceId={}, taskId={}, sessionId={}, model={}",
                traceId, taskId, sessionId, model);
        try {
            agentClient.submit(request);
        } catch (AgentClientException exception) {
            log.error("提交Python Agent任务失败, traceId={}, taskId={}, reason={}",
                    traceId, taskId, exception.getReason(), exception);
            if (exception.getReason() == AgentClientException.Reason.CAPACITY_EXCEEDED) {
                throw new AgentBusinessException("AI任务队列已满，请稍后重试");
            }
            try {
                agentClient.cancelTask(taskId);
            } catch (Exception ignored) {
                // 提交是否已在Python落地未知，尽力清理。
            }
            throw new AgentBusinessException("Agent服务暂时不可用，请稍后重试");
        } catch (RuntimeException exception) {
            log.error("提交Python Agent任务失败, taskId={}", taskId, exception);
            try {
                // 尽力清理：尝试取消可能已在Python端创建的任务
                agentClient.cancelTask(taskId);
            } catch (Exception ignored) {
                // Python可能尚未创建任务，此处忽略异常
            }
            throw new AgentBusinessException("Agent服务暂时不可用，请稍后重试");
        }

        // 4. 保存用户消息
        sessionMapper.getBySessionIdForUpdate(sessionId); // 锁定会话以安全更新
        int userSeqNo = messageMapper.getNextSeqNo(sessionId);
        AgentMessage userMessage = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .sessionId(sessionId)
                .taskId(taskId)
                .role(1) // 1: user
                .content(dto.getQuery())
                .contentType("text")
                .seqNo(userSeqNo)
                .build();
        messageMapper.insert(userMessage);
        evictMessageCacheAfterCommit(session.getId()); // 事务提交后使缓存失效

        // 5. 更新会话元数据，并启动后续处理
        sessionMapper.incrementMessageCount(session.getId(), taskId);
        startEventSubscriptionAfterCommit(taskId); // 事务提交后启动事件流
        scheduleSummaryAfterCommit(sessionId); // 事务提交后调度摘要任务

        return toSubmitVO(task);
    }

    /**
     * 分页查询当前用户的会话列表。
     *
     * @param dto 分页和筛选条件
     * @return 分页结果 (PageResult)
     */
    @Override
    public PageResult pageQuerySessions(AgentSessionPageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<AgentSession> page = sessionMapper.pageQuery(userId, dto.getStatus());
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 获取指定会话的详细信息，包括所有历史消息。
     *
     * @param sessionId 会话的唯一标识符
     * @return 包含会话信息和消息列表的详细视图对象
     */
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

        // 从缓存或数据库加载消息
        List<AgentMessage> messages = loadMessages(session.getId(), sessionId);
        List<AgentMessageVO> messageVOs = messages.stream()
                .map(this::convertMessageVO)
                .collect(Collectors.toList());

        return AgentSessionDetailVO.builder()
                .session(vo)
                .messages(messageVOs)
                .build();
    }

    /**
     * 更新会话信息（如标题或状态）。
     *
     * @param dto 包含要更新的会话信息
     */
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
        evictMessageCacheAfterCommit(session.getId()); // 更新后使缓存失效
    }

    /**
     * 分页查询指定会话的任务列表。
     *
     * @param dto 分页和筛选条件
     * @return 分页结果 (PageResult)
     */
    @Override
    public PageResult pageQueryTasks(AgentTaskPageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<AgentTask> page = taskMapper.pageQuery(dto.getSessionId(), userId, dto.getStatus());
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 获取单个任务的详细信息。
     *
     * @param taskId 任务的唯一标识符
     * @return 任务的详细视图对象
     */
    @Override
    public AgentTaskVO getTaskDetail(String taskId) {
        AgentTask task = getOwnedTask(taskId);
        AgentTaskVO vo = new AgentTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    /**
     * 取消一个正在运行的任务。
     *
     * @param taskId 要取消的任务的唯一标识符
     */
    @Override
    @Transactional
    public void cancelTask(String taskId) {
        AgentTask task = getOwnedTask(taskId);
        // 状态检查，只有运行中的任务可以被取消
        if (task.getStatus() == 4) { // 4: cancelled
            return;
        }
        if (task.getStatus() == 2 || task.getStatus() == 3) { // 2: success, 3: failed
            throw new AgentBusinessException("任务已结束，无法取消");
        }
        AgentTaskStatusResponse pythonStatus;
        try {
            // 调用Python Agent取消任务
            pythonStatus = agentClient.cancelTask(taskId);
        } catch (RuntimeException exception) {
            log.error("取消Python Agent任务失败, taskId={}", taskId, exception);
            throw new AgentBusinessException("Agent任务取消失败，请稍后重试");
        }
        if (pythonStatus == null || !"cancelled".equals(pythonStatus.status())) {
            throw new AgentBusinessException("任务已经结束，无法取消");
        }
        // 更新本地任务状态为已取消
        taskMapper.transitionStatus(taskId, 4, task.getProgress(), null,
                null, List.of(0, 1)); // 仅当状态为 0 或 1 时更新
    }

    /**
     * 获取指定任务在某个序列号之后的所有事件。
     * 用于客户端断线重连后恢复事件流。
     *
     * @param taskId    任务ID
     * @param lastSeqNo 客户端最后收到的事件序列号
     * @return 事件列表
     */
    @Override
    public List<AgentEvent> getEventsAfterSeqNo(String taskId, Integer lastSeqNo) {
        getOwnedTask(taskId); // 权限检查
        if (lastSeqNo == null || lastSeqNo < 0) {
            lastSeqNo = 0;
        }
        return eventMapper.listByTaskIdAfterSeqNo(taskId, lastSeqNo);
    }

    // ---- 私有方法 ----

    /**
     * 将AgentMessage实体转换为VO。
     */
    private AgentMessageVO convertMessageVO(AgentMessage msg) {
        AgentMessageVO vo = new AgentMessageVO();
        BeanUtils.copyProperties(msg, vo);
        if (msg.getRole() != null && msg.getRole() == 2) {
            vo.setCitations(citationMapper.listByMessageId(msg.getMessageId()).stream().map(value -> {
                AgentCitationVO citation = new AgentCitationVO();
                BeanUtils.copyProperties(value, citation);
                return citation;
            }).collect(Collectors.toList()));
        } else {
            vo.setCitations(List.of());
        }
        return vo;
    }

    /**
     * 获取并验证任务所有权。
     *
     * @param taskId 任务ID
     * @return 如果任务存在且属于当前用户，则返回任务实体
     * @throws AgentBusinessException 如果任务不存在或用户无权访问
     */
    private AgentTask getOwnedTask(String taskId) {
        Long userId = BaseContext.getCurrentId();
        AgentTask task = taskMapper.getByTaskIdAndUserId(taskId, userId);
        if (task == null) {
            throw new AgentBusinessException("任务不存在或无权访问");
        }
        return task;
    }

    /**
     * 将AgentTask实体转换为提交响应VO。
     */
    private AgentSubmitVO toSubmitVO(AgentTask task) {
        return AgentSubmitVO.builder()
                .taskId(task.getTaskId())
                .sessionId(task.getSessionId())
                .eventsUrl("/admin/agent/tasks/" + task.getTaskId() + "/events")
                .status(task.getStatus())
                .assistantMessageId(task.getAssistantMessageId())
                .errorMsg(task.getErrorMsg())
                .build();
    }

    /**
     * 计算请求的SHA-256哈希值，用于幂等性检查。
     *
     * @param taskId    任务ID
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param query     用户查询
     * @param model     使用的模型
     * @return SHA-256哈希字符串
     */
    private String requestHash(String taskId, String sessionId, Long userId, String query, String model,
                               String kbId) {
        String input = String.join("\u0000", String.valueOf(taskId), String.valueOf(sessionId),
                String.valueOf(userId), String.valueOf(query), String.valueOf(model), String.valueOf(kbId));
        return sha256(input);
    }

    private String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private boolean sameNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String normalizeKbId(String kbId) {
        return kbId == null || kbId.isBlank() ? null : kbId;
    }

    private boolean matchesStoredRequestHash(String storedHash, String currentHash, String taskId,
                                             String sessionId, Long userId, String query, String model,
                                             boolean allowLegacyHash) {
        if (storedHash == null || storedHash.equals(currentHash)) {
            return true;
        }
        return allowLegacyHash && storedHash.equals(legacyRequestHash(taskId, sessionId, userId, query, model));
    }

    private String legacyRequestHash(String taskId, String sessionId, Long userId, String query, String model) {
        String input = String.join("\u0000", String.valueOf(taskId), String.valueOf(sessionId),
                String.valueOf(userId), String.valueOf(query), String.valueOf(model));
        return sha256(input);
    }

    private void validateKnowledgeBase(String kbId, Long userId) {
        if (kbId == null || kbId.isBlank()) {
            return;
        }
        AgentKnowledgeBase kb = knowledgeMapper.getOwnedBase(kbId, userId);
        if (kb == null || kb.getStatus() == null || kb.getStatus() != 1) {
            throw new AgentBusinessException("知识库不存在或未启用");
        }
    }

    private void bindKnowledgeBase(AgentSession session, String requestedKbId, Long userId) {
        if (session.getKbId() == null || session.getKbId().isBlank()) {
            validateKnowledgeBase(requestedKbId, userId);
            if (requestedKbId != null && !requestedKbId.isBlank()) {
                session.setKbId(requestedKbId);
                sessionMapper.update(AgentSession.builder().id(session.getId()).kbId(requestedKbId).build());
            }
            return;
        }
        if (!sameNullable(session.getKbId(), requestedKbId)) {
            throw new AgentBusinessException("会话已绑定其他知识库，不能切换");
        }
        validateKnowledgeBase(session.getKbId(), userId);
    }

    private AgentKnowledgeScope buildKnowledgeScope(String kbId, Long userId) {
        if (!agentProperties.isRagEnabled() || kbId == null || kbId.isBlank()) {
            return null;
        }
        validateKnowledgeBase(kbId, userId);
        Map<String, Integer> versions = knowledgeMapper.listReadyDocuments(kbId).stream()
                .filter(document -> document.getActiveVersion() != null)
                .collect(Collectors.toMap(AgentKnowledgeDocument::getDocumentId,
                        AgentKnowledgeDocument::getActiveVersion, (left, right) -> right));
        return new AgentKnowledgeScope(kbId, versions,
                agentProperties.getRagTopK(), agentProperties.getRagScoreThreshold());
    }

    /**
     * 构建用于Python Agent的历史消息列表。
     * <p>
     * 包含长期记忆（会话摘要）和短期记忆（最近的对话）。
     * 消息会根据字符数限制进行截断。
     *
     * @param sessionId   会话的字符串ID
     * @param sessionDbId 会话在数据库中的长整型ID (未使用，但保留以兼容旧逻辑)
     * @return 历史消息列表
     */
    private List<AgentHistoryMessage> buildHistory(String sessionId, Long sessionDbId) {
        java.util.LinkedList<AgentHistoryMessage> history = new java.util.LinkedList<>();
        // 1. 添加会话摘要作为系统消息（长期记忆）
        com.sky.entity.AgentSessionSummary summary = summaryMapper.getBySessionId(sessionId);
        if (summary != null && summary.getSummary() != null && !summary.getSummary().isBlank()) {
            history.add(new AgentHistoryMessage("system", "会话历史摘要：\n" + summary.getSummary()));
        }
        // 2. 添加最近的对话消息（短期记忆）
        List<AgentMessage> messages = messageMapper.listRecentBySessionId(sessionId,
                agentProperties.getSummaryRecentMessageCount());
        int remainingCharacters = agentProperties.getContextCharacterLimit();
        // 从后往前遍历，优先保留最新的消息
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
            // 如果内容超长，截断以满足字符限制
            if (content.length() > remainingCharacters) {
                content = content.substring(content.length() - remainingCharacters);
            }
            history.addFirst(new AgentHistoryMessage(role, content)); // 保持正确的顺序
            remainingCharacters -= content.length();
        }
        return history;
    }

    private final AgentSessionSummaryMapper summaryMapper;

    /**
     * 在事务提交后调度会话摘要任务。
     *
     * @param sessionId 会话ID
     */
    private void scheduleSummaryAfterCommit(String sessionId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            summaryService.scheduleIfNeeded(sessionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                summaryService.scheduleIfNeeded(sessionId);
            }
        });
    }

    /**
     * 从缓存或数据库加载会话消息。
     *
     * @param sessionDbId 会话数据库ID，用作缓存键
     * @param sessionId   会话字符串ID，用于数据库查询
     * @return 消息列表
     */
    private List<AgentMessage> loadMessages(Long sessionDbId, String sessionId) {
        List<AgentMessage> messages = messageCacheService.get(sessionDbId);
        if (messages == null) {
            messages = messageMapper.listBySessionIdOrderBySeqNo(sessionId);
            messageCacheService.put(sessionDbId, messages);
        }
        return messages;
    }

    /**
     * 在事务提交后使会话的消息缓存失效。
     *
     * @param sessionDbId 会话数据库ID
     */
    private void evictMessageCacheAfterCommit(Long sessionDbId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            messageCacheService.evict(sessionDbId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messageCacheService.evict(sessionDbId);
            }
        });
    }

    /**
     * 在事务提交后启动事件流协调器。
     * 如果事务回滚，则尝试取消Python端的任务。
     *
     * @param taskId 任务ID
     */
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
                // 如果本地事务回滚，尽力取消远程Python任务
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

    /**
     * 将查询字符串截断为合适的标题长度。
     *
     * @param query 用户查询
     * @return 截断后的标题
     */
    private String truncateTitle(String query) {
        if (query == null) {
            return "新对话";
        }
        return query.length() > 30 ? query.substring(0, 30) + "..." : query;
    }
}
