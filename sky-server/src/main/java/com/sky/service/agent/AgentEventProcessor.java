package com.sky.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentEvent;
import com.sky.entity.AgentMessage;
import com.sky.entity.AgentMessageCitation;
import com.sky.entity.AgentSession;
import com.sky.entity.AgentTask;
import com.sky.mapper.AgentEventMapper;
import com.sky.mapper.AgentCitationMapper;
import com.sky.mapper.AgentMessageMapper;
import com.sky.mapper.AgentSessionMapper;
import com.sky.mapper.AgentTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Agent事件处理器
 * <p>
 * 核心职责：
 * 1.  <b>幂等处理</b>：消费来自Python Agent的SSE事件，并确保每个事件只被处理一次，即使在分布式或重试场景下。
 * 2.  <b>持久化</b>：将事件日志（`agent_event`）持久化到数据库，用于审计、调试和断线重连。
 * 3.  <b>状态推进</b>：根据事件类型，驱动相关的业务实体（如`AgentTask`, `AgentMessage`, `AgentSession`）的状态变更。
 * 4.  <b>数据转换</b>：将事件中的JSON数据转换为Java实体，并写入相应的业务表。
 * 5.  <b>缓存管理</b>：在事务提交后，使相关的缓存（如消息列表）失效，确保数据一致性。
 * </p>
 * <p>
 * 所有事件处理都在一个事务中完成，保证了数据操作的原子性。
 * </p>
 */
@Service
@Slf4j
public class AgentEventProcessor {

    private final AgentEventMapper eventMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentSessionMapper sessionMapper;
    private final AgentCitationMapper citationMapper;
    private final ObjectMapper objectMapper;
    private final AgentMessageCacheService messageCacheService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public AgentEventProcessor(AgentEventMapper eventMapper, AgentTaskMapper taskMapper,
                               AgentMessageMapper messageMapper, AgentSessionMapper sessionMapper,
                               AgentCitationMapper citationMapper, ObjectMapper objectMapper,
                               AgentMessageCacheService messageCacheService,
                               MeterRegistry meterRegistry) {
        this.eventMapper = eventMapper;
        this.taskMapper = taskMapper;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.citationMapper = citationMapper;
        this.objectMapper = objectMapper;
        this.messageCacheService = messageCacheService;
        this.meterRegistry = meterRegistry;
    }

    public AgentEventProcessor(AgentEventMapper eventMapper, AgentTaskMapper taskMapper,
                               AgentMessageMapper messageMapper, AgentSessionMapper sessionMapper,
                               AgentCitationMapper citationMapper, ObjectMapper objectMapper,
                               AgentMessageCacheService messageCacheService) {
        this(eventMapper, taskMapper, messageMapper, sessionMapper, citationMapper,
                objectMapper, messageCacheService, null);
    }

    /**
     * 兼容性构造函数，主要用于保持现有单元测试和手工实例化的代码可用。
     * 在生产环境中，应使用带有所有依赖的
     * {@link #AgentEventProcessor(AgentEventMapper, AgentTaskMapper, AgentMessageMapper, AgentSessionMapper, AgentCitationMapper, ObjectMapper, AgentMessageCacheService)}
     * 构造函数。
     */
    public AgentEventProcessor(AgentEventMapper eventMapper, AgentTaskMapper taskMapper,
                               AgentMessageMapper messageMapper, AgentSessionMapper sessionMapper,
                               ObjectMapper objectMapper) {
        this(eventMapper, taskMapper, messageMapper, sessionMapper, null, objectMapper, null);
    }

    /**
     * 处理一条来自Python Agent的流式事件。
     * <p>
     * 此方法是事件处理的核心入口，它在一个事务中执行以下操作：
     * 1.  <b>校验</b>：检查任务是否存在，以及事件是否与当前任务状态兼容。
     * 2.  <b>幂等写入</b>：尝试将事件插入`agent_event`表。如果插入失败（因为事件已存在），则跳过后续处理。
     * 3.  <b>状态分派</b>：根据事件类型（如`task_start`, `task_end`等），调用相应的私有方法来更新业务状态。
     * </p>
     *
     * @param event 从Python Agent接收到的流式事件对象。
     * @return 如果事件被成功处理，则返回 {@code true}；如果事件因重复或状态不兼容而被跳过，则返回 {@code false}。
     */
    @Transactional
    public boolean process(AgentStreamEvent event) {
        log.debug("处理Agent事件: traceId={}, taskId={}, seqNo={}, eventType={}",
                event.traceId(), event.taskId(), event.seqNo(), event.event());
        // 1. 校验任务存在且状态兼容
        AgentTask task = taskMapper.getByTaskId(event.taskId());
        if (task == null || !isCompatible(task.getStatus(), event.event())) {
            log.warn("跳过不兼容的事件: taskId={}, taskStatus={}, eventType={}",
                    event.taskId(), task != null ? task.getStatus() : "null", event.event());
            return false;
        }

        // 2. 幂等写入事件日志
        AgentEvent stored = AgentEvent.builder()
                .eventId(event.taskId() + ":" + event.seqNo())
                .taskId(event.taskId())
                .seqNo(event.seqNo())
                .eventType(event.event())
                .data(writeJson(event.data()))
                .build();
        if (eventMapper.insertIfAbsent(stored) == 0) {
            log.info("跳过已处理的重复事件: eventId={}", stored.getEventId());
            return false;
        }
        if (meterRegistry != null) {
            meterRegistry.counter("sky.agent.events", "event_type", event.event()).increment();
        }

        // 3. 根据事件类型推进业务状态
        boolean success = switch (event.event()) {
            case "task_start" -> startTask(task);
            case "token" -> acceptToken(task);
            case "task_end" -> completeTask(task, event.data());
            case "task_error" -> failTask(task, event.data());
            case "task_cancelled" -> cancelTask(task);
            default -> true; // 对于不改变状态的事件（如中间步骤），直接返回成功
        };
        if (!success) {
            log.warn("事件处理失败（状态转换失败）: eventId={}", stored.getEventId());
        }
        return success;
    }

    // ---- 任务状态机转换 ----

    /**
     * 处理 `task_start` 事件：将任务状态从 0 (等待) 转换为 1 (运行中)。
     * 如果任务已经是运行中或已取消，则视为兼容，直接返回成功。
     */
    private boolean startTask(AgentTask task) {
        if (task.getStatus() == 1 || task.getStatus() == 4) {
            return true;
        }
        return taskMapper.transitionStatus(task.getTaskId(), 1, 1, null, null,
                List.of(0)) == 1;
    }

    /**
     * 处理 `token` 事件：如果任务还在等待状态，则将其启动，状态 0 → 1。
     * 这确保了即使 `task_start` 事件丢失或延迟，任务也能在收到第一个实质性输出时进入运行状态。
     */
    private boolean acceptToken(AgentTask task) {
        if (task.getStatus() == 0) {
            return taskMapper.transitionStatus(task.getTaskId(), 1, 1, null, null,
                    List.of(0)) == 1;
        }
        return task.getStatus() == 1 || task.getStatus() == 4;
    }

    /**
     * 处理 `task_end` 事件：将任务状态从 1 (运行中) 转换为 2 (已完成)。
     * 同时，创建一个代表AI助手的消息（`AgentMessage`），并将其与任务关联。
     * 如果事件中包含引用（citations），也会一并持久化。
     */
    private boolean completeTask(AgentTask task, JsonNode data) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        int transitioned = taskMapper.transitionStatus(task.getTaskId(), 2, 100,
                messageId, null, List.of(0, 1));
        if (transitioned == 0) {
            return false;
        }

        // 写入assistant回复消息并更新会话消息计数
        AgentSession session = sessionMapper.getBySessionIdForUpdate(task.getSessionId());
        AgentMessage assistantMessage = AgentMessage.builder()
                .messageId(messageId)
                .sessionId(task.getSessionId())
                .taskId(task.getTaskId())
                .role(2) // 2 代表 assistant
                .content(text(data, "result", ""))
                .contentType("markdown")
                .seqNo(messageMapper.getNextSeqNo(task.getSessionId()))
                .build();
        if (messageMapper.insertIfAbsent(assistantMessage) == 1) {
            persistCitations(messageId, session, data);
            if (session != null) {
                sessionMapper.incrementMessageCount(session.getId(), task.getTaskId());
            }
            if (session != null) {
                evictMessageCacheAfterCommit(session.getId());
            }
        }
        return true;
    }

    /**
     * 从 `task_end` 事件中提取并持久化知识库引用（citations）。
     * 每个引用都与新创建的助手消息关联。
     */
    private void persistCitations(String messageId, AgentSession session, JsonNode data) {
        if (citationMapper == null || session == null || session.getKbId() == null
                || data == null || !data.path("citations").isArray()) {
            return;
        }
        int count = 0;
        for (JsonNode item : data.path("citations")) {
            if (count >= 5) { // 最多只持久化5个引用
                break;
            }
            String kbId = text(item, "kb_id", null);
            String documentId = text(item, "document_id", null);
            String chunkId = text(item, "chunk_id", null);
            String fileName = text(item, "file_name", null);
            String quote = text(item, "quote", null);
            JsonNode version = item.get("document_version");
            JsonNode score = item.get("score");
            if (!session.getKbId().equals(kbId) || documentId == null || chunkId == null
                    || fileName == null || quote == null || version == null || !version.canConvertToInt()
                    || score == null || !score.isNumber()) {
                log.warn("忽略无效的Agent引用, messageId={}, citation={}", messageId, item);
                continue;
            }
            AgentMessageCitation citation = new AgentMessageCitation();
            citation.setMessageId(messageId);
            citation.setKbId(kbId);
            citation.setDocumentId(documentId);
            citation.setDocumentVersion(version.asInt());
            citation.setChunkId(chunkId);
            citation.setFileName(fileName);
            citation.setPageNo(item.path("page_no").canConvertToInt() ? item.path("page_no").asInt() : null);
            citation.setScore(BigDecimal.valueOf(score.asDouble()));
            citation.setQuote(quote);
            citationMapper.insertIfAbsent(citation);
            count++;
        }
    }

    /**
     * 在数据库事务成功提交后，使指定会话的消息缓存失效。
     * 确保客户端在下次请求时能获取到最新的消息列表。
     */
    private void evictMessageCacheAfterCommit(Long sessionDbId) {
        if (messageCacheService == null) {
            return;
        }
        // 如果当前没有活动的事务，则立即清除缓存
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            messageCacheService.evict(sessionDbId);
            return;
        }
        // 否则，注册一个同步回调，在事务提交后执行缓存清理
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messageCacheService.evict(sessionDbId);
            }
        });
    }

    /**
     * 处理 `task_error` 事件：将任务状态转换为 3 (失败)，并记录错误信息。
     */
    private boolean failTask(AgentTask task, JsonNode data) {
        return taskMapper.transitionStatus(task.getTaskId(), 3, task.getProgress(), null,
                text(data, "error_msg", "Agent执行失败"), List.of(0, 1)) == 1;
    }

    /**
     * 处理 `task_cancelled` 事件：将任务状态转换为 4 (已取消)。
     * 如果任务已经处于取消状态，则直接返回成功。
     */
    private boolean cancelTask(AgentTask task) {
        if (task.getStatus() == 4) {
            return true;
        }
        return taskMapper.transitionStatus(task.getTaskId(), 4, task.getProgress(), null,
                null, List.of(0, 1)) == 1;
    }

    // ---- 辅助方法 ----

    /**
     * 检查传入的事件类型是否与当前任务状态兼容。
     * <p>
     * 主要规则：
     * - 终态（已完成、已失败）的任务不再接受任何事件。
     * - 已取消的任务可以接受非终态事件（如 `token`），但不能再次完成或失败。
     * </p>
     *
     * @param status    当前任务的状态码。
     * @param eventType 传入事件的类型字符串。
     * @return 如果兼容，则返回 {@code true}。
     */
    private boolean isCompatible(Integer status, String eventType) {
        if (status == null) {
            return false;
        }
        // 已完成或已失败：不再接受任何事件
        if (status == 2 || status == 3) {
            return false;
        }
        // 已取消：不再接受终态事件
        if (status == 4) {
            return !"task_end".equals(eventType) && !"task_error".equals(eventType);
        }
        return true;
    }

    /**
     * 将 {@link JsonNode} 对象序列化为JSON字符串。
     * 如果输入为 {@code null}，则返回表示JSON `null`的字符串。
     *
     * @throws IllegalArgumentException 如果序列化失败。
     */
    private String writeJson(JsonNode data) {
        try {
            return objectMapper.writeValueAsString(data == null ? objectMapper.nullNode() : data);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化Agent事件数据", exception);
        }
    }

    /**
     * 从JSON数据中安全地提取指定字段的文本值。
     *
     * @param data         JSON数据节点。
     * @param field        要提取的字段名。
     * @param defaultValue 如果字段不存在、为null或无法转换为文本，则返回此默认值。
     * @return 提取到的文本值或默认值。
     */
    private String text(JsonNode data, String field, String defaultValue) {
        if (data == null || !data.hasNonNull(field)) {
            return defaultValue;
        }
        return data.get(field).asText(defaultValue);
    }
}
