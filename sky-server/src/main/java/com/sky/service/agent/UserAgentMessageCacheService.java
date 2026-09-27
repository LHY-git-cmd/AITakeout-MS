package com.sky.service.agent;

import com.sky.entity.AgentMessage;
import com.sky.properties.AgentProperties;
import com.sky.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 用户端Agent消息缓存，使用独立Redis前缀并在Redis异常时回源数据库。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAgentMessageCacheService {
    private final RedisUtil redisUtil;
    private final AgentProperties properties;

    @SuppressWarnings("unchecked")
    public List<AgentMessage> get(Long sessionDbId) {
        if (!properties.isMessageCacheEnabled()) return null;
        try {
            Object value = redisUtil.get(key(sessionDbId));
            return value instanceof List<?> list ? (List<AgentMessage>) list : null;
        } catch (RuntimeException exception) {
            log.debug("用户Agent消息缓存读取失败，回源数据库, sessionDbId={}", sessionDbId);
            return null;
        }
    }

    public void put(Long sessionDbId, List<AgentMessage> messages) {
        if (!properties.isMessageCacheEnabled()) return;
        try {
            redisUtil.set(key(sessionDbId), messages,
                    properties.getMessageCacheTtlSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            log.debug("用户Agent消息缓存写入失败, sessionDbId={}", sessionDbId);
        }
    }

    public void evict(Long sessionDbId) {
        if (!properties.isMessageCacheEnabled()) return;
        try {
            redisUtil.delete(key(sessionDbId));
        } catch (RuntimeException exception) {
            log.debug("用户Agent消息缓存清理失败, sessionDbId={}", sessionDbId);
        }
    }

    private String key(Long sessionDbId) {
        return properties.getUserMessageCacheKeyPrefix() + sessionDbId;
    }
}
