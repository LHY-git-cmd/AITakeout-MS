package com.sky.service.agent;

import com.sky.entity.AgentMessage;
import com.sky.properties.AgentProperties;
import com.sky.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Agent会话完整消息缓存，Redis不可用时自动降级。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentMessageCacheService {

    /** Redis 操作工具 */
    private final RedisUtil redisUtil;
    /** Agent 相关配置（缓存开关、TTL、key 前缀等） */
    private final AgentProperties agentProperties;

    /**
     * 读取指定会话的完整消息缓存。
     *
     * @param sessionId 会话ID
     * @return 缓存中的消息列表；若缓存未命中、Redis 不可用或数据已损坏则返回 {@code null}
     */
    public List<AgentMessage> get(Long sessionId) {
        // 缓存开关关闭，直接返回 null 让调用方走数据库查询
        if (!agentProperties.isMessageCacheEnabled()) {
            return null;
        }
        try {
            Object value = redisUtil.get(key(sessionId));
            // Redis 中不存在或类型不匹配，视为缓存未命中
            if (!(value instanceof List<?> values)) {
                return null;
            }
            // 防御性校验：缓存数据里混入了非 AgentMessage 对象 → 脏数据，清理掉
            if (values.stream().anyMatch(valueItem -> !(valueItem instanceof AgentMessage))) {
                redisUtil.delete(key(sessionId));
                return null;
            }
            @SuppressWarnings("unchecked")
            List<AgentMessage> messages = (List<AgentMessage>) values;
            return messages;
        } catch (RuntimeException exception) {
            // Redis 挂了 / 网络超时，降级为 null，由调用方回源数据库
            log.warn("读取Agent消息缓存失败，降级查询数据库，sessionId={}", sessionId, exception);
            return null;
        }
    }

    /**
     * 将指定会话的完整消息写入 Redis 缓存。
     *<p>
     * TTL 由 {@code sky.agent.message-cache-ttl-seconds} 配置，默认 1800 秒（30 分钟）。
     * Redis 不可用时静默失败不抛异常，不影响主流程。
     *
     * @param sessionId 会话ID
     * @param messages  要缓存的消息列表
     */
    public void put(Long sessionId, List<AgentMessage> messages) {
        if (!agentProperties.isMessageCacheEnabled()) {
            return;
        }
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            redisUtil.set(key(sessionId), messages,
                    agentProperties.getMessageCacheTtlSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            // 写缓存失败不阻塞主业务，仅记录告警
            log.warn("写入Agent消息缓存失败，sessionId={}", sessionId, exception);
        }
    }

    /**
     * 清理指定会话的消息缓存。
     *
     * @param sessionId 会话ID
     */
    public void evict(Long sessionId) {
        if (!agentProperties.isMessageCacheEnabled()) {
            return;
        }
        try {
            redisUtil.delete(key(sessionId));
        } catch (RuntimeException exception) {
            log.warn("清理Agent消息缓存失败，sessionId={}", sessionId, exception);
        }
    }

    /** 构建 Redis key：{prefix}{sessionId}，前缀可在 AgentProperties 中配置。 */
    private String key(Long sessionId) {
        return agentProperties.getMessageCacheKeyPrefix() + sessionId;
    }
}
