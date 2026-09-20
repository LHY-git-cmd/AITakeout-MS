package com.sky.service.notification;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.mapper.OutboxEventMapper;
import com.sky.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 将 Outbox 事件推送到实时通道；持久化通知始终是最终事实来源。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private final OutboxEventMapper outboxMapper;
    private final WebSocketServer webSocketServer;

    public int publishBatch() {
        int published = 0;
        LocalDateTime now = LocalDateTime.now();
        for (var event : outboxMapper.findDue(now, 100)) {
            try {
                JSONObject payload = JSON.parseObject(event.getPayloadJson());
                Long userId = payload.getLong("userId");
                payload.remove("userId");
                webSocketServer.sendNotificationToUser(userId, payload.toJSONString());
                if (outboxMapper.markPublished(event.getId(), now, now) == 1) published++;
            } catch (RuntimeException exception) {
                int attempts = event.getAttemptCount() + 1;
                String status = attempts >= 8 ? "DEAD" : "RETRY";
                long delayMinutes = 1L << Math.min(attempts - 1, 4);
                outboxMapper.markFailed(event.getId(), event.getAttemptCount(), status, attempts,
                        now.plusMinutes(delayMinutes), abbreviate(exception.getMessage()), now);
                log.warn("通知 Outbox 发布失败：eventId={}, attempts={}", event.getEventId(), attempts, exception);
            }
        }
        return published;
    }

    private static String abbreviate(String value) {
        if (value == null) return "UNKNOWN";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
