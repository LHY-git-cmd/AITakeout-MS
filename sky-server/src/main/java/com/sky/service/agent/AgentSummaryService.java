package com.sky.service.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.agent.AgentClient;
import com.sky.entity.AgentMessage;
import com.sky.entity.AgentSessionSummary;
import com.sky.mapper.AgentMessageMapper;
import com.sky.mapper.AgentSessionSummaryMapper;
import com.sky.properties.AgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 异步生成会话摘要；摘要失败不影响当前对话。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentSummaryService {
    private static final String SUMMARY_PROMPT = """
            请将以下AI对话历史整理为结构化摘要，只输出以下固定字段：
            用户目标：
            已确认事实：
            关键决策：
            用户偏好：
            业务约束：
            未解决问题：
            待办事项：
            不确定信息：
            保留具体日期、数字、ID和约束；没有内容的字段写“无”。
            """;

    private final AgentClient agentClient;
    private final AgentMessageMapper messageMapper;
    private final AgentSessionSummaryMapper summaryMapper;
    private final AgentProperties properties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public void scheduleIfNeeded(String sessionId) {
        List<AgentMessage> messages = messageMapper.listBySessionIdOrderBySeqNo(sessionId);
        AgentSessionSummary current = summaryMapper.getBySessionId(sessionId);
        int until = current == null ? 0 : current.getSummaryUntilSeq();
        List<AgentMessage> unsummarized = messages.stream()
                .filter(message -> message.getSeqNo() != null && message.getSeqNo() > until)
                .toList();
        if (messages.size() <= properties.getSummaryMessageThreshold()
                && estimateTokens(unsummarized) <= properties.getSummaryTokenThreshold()) {
            return;
        }
        int keep = Math.min(properties.getSummaryRecentMessageCount(), unsummarized.size());
        if (unsummarized.size() <= keep) return;
        int cutoff = unsummarized.get(unsummarized.size() - keep).getSeqNo() - 1;
        executor.submit(() -> generate(sessionId, current, messages, cutoff));
    }

    private void generate(String sessionId, AgentSessionSummary snapshot,
                          List<AgentMessage> messages, int cutoff) {
        try {
            int expectedVersion = snapshot == null ? 0 : snapshot.getVersion();
            int previousUntil = snapshot == null ? 0 : snapshot.getSummaryUntilSeq();
            StringBuilder source = new StringBuilder(SUMMARY_PROMPT).append("\n");
            if (snapshot != null && snapshot.getSummary() != null) {
                source.append("已有摘要：\n").append(snapshot.getSummary()).append("\n");
            }
            source.append("新增历史：\n");
            messages.stream()
                    .filter(message -> message.getSeqNo() != null
                            && message.getSeqNo() > previousUntil
                            && message.getSeqNo() <= cutoff)
                    .forEach(message -> source.append(role(message)).append("：")
                            .append(message.getContent()).append("\n"));
            JSONObject response = JSON.parseObject(agentClient.query(java.util.Map.of(
                    "query", source.toString(), "temperature", 0.1)));
            String summary = response.getString("result");
            if (summary == null || summary.isBlank()) return;
            if (snapshot == null) {
                AgentSessionSummary created = new AgentSessionSummary();
                created.setSessionId(sessionId);
                created.setSummary(summary);
                created.setSummaryUntilSeq(cutoff);
                created.setVersion(0);
                try {
                    summaryMapper.insert(created);
                } catch (RuntimeException duplicate) {
                    log.debug("摘要已由并发任务创建, sessionId={}", sessionId);
                }
            } else if (summaryMapper.updateOptimistic(sessionId, summary, cutoff, expectedVersion) == 0) {
                log.debug("摘要版本已变化，丢弃旧摘要, sessionId={}", sessionId);
            }
        } catch (Exception exception) {
            log.warn("生成Agent会话摘要失败, sessionId={}", sessionId, exception);
        }
    }

    private String role(AgentMessage message) {
        return switch (message.getRole()) {
            case 1 -> "用户";
            case 2 -> "助手";
            case 3 -> "系统";
            default -> "消息";
        };
    }

    private int estimateTokens(List<AgentMessage> messages) {
        return messages.stream().mapToInt(message ->
                message.getContent() == null ? 0 : (message.getContent().length() + 1) / 2).sum();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
