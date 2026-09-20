package com.sky.service.notification;

import com.alibaba.fastjson.JSON;
import com.sky.entity.OutboxEvent;
import com.sky.entity.UserNotification;
import com.sky.mapper.OutboxEventMapper;
import com.sky.mapper.UserNotificationMapper;
import com.sky.service.notification.model.NotificationModels.NotificationCommand;
import com.sky.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 持久化通知服务；通知与 Outbox 在同一事务内写入。 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final UserNotificationMapper notificationMapper;
    private final OutboxEventMapper outboxMapper;

    @Transactional
    public UserNotification record(NotificationCommand command) {
        validate(command);
        UserNotification existing = notificationMapper.findByBusinessKey(command.businessKey());
        if (existing != null) return existing;
        LocalDateTime now = LocalDateTime.now();
        String eventId = "NTF-" + UUID.randomUUID();
        UserNotification notification = UserNotification.builder().eventId(eventId)
                .businessKey(command.businessKey()).userId(command.userId()).type(command.type().name())
                .title(command.title()).content(command.content()).orderId(command.orderId()).createTime(now).build();
        try {
            notificationMapper.insert(notification);
            Map<String, Object> payload = Map.of("eventId", eventId, "type", command.type().name(),
                    "notificationId", notification.getId(), "createdAt", now.toString(), "userId", command.userId());
            outboxMapper.insert(OutboxEvent.builder().eventId(eventId).businessKey(command.businessKey())
                    .aggregateType(command.orderId() == null ? "USER" : "ORDER")
                    .aggregateId(String.valueOf(command.orderId() == null ? command.userId() : command.orderId()))
                    .eventType("USER_NOTIFICATION").payloadJson(JSON.toJSONString(payload)).status("PENDING")
                    .attemptCount(0).nextAttemptAt(now).createTime(now).updateTime(now).build());
            return notification;
        } catch (DuplicateKeyException exception) {
            UserNotification replay = notificationMapper.findByBusinessKey(command.businessKey());
            if (replay != null) return replay;
            throw exception;
        }
    }

    public List<NotificationVO> list(long userId, Long beforeId, LocalDateTime since, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return notificationMapper.list(userId, beforeId, since, safeLimit).stream().map(NotificationService::view).toList();
    }

    public long unreadCount(long userId) {
        return notificationMapper.countUnread(userId);
    }

    public void markRead(long userId, long notificationId) {
        notificationMapper.markRead(notificationId, userId, LocalDateTime.now());
    }

    public void markAllRead(long userId) {
        notificationMapper.markAllRead(userId, LocalDateTime.now());
    }

    public int deleteExpired() {
        return notificationMapper.deleteBefore(LocalDateTime.now().minusDays(90));
    }

    private static NotificationVO view(UserNotification value) {
        return NotificationVO.builder().id(value.getId()).eventId(value.getEventId()).type(value.getType())
                .title(value.getTitle()).content(value.getContent()).orderId(value.getOrderId())
                .read(value.getReadAt() != null).createTime(value.getCreateTime()).build();
    }

    private static void validate(NotificationCommand command) {
        if (command == null || command.businessKey() == null || command.businessKey().isBlank()) throw new IllegalArgumentException("通知业务键不能为空");
        if (command.userId() <= 0 || command.type() == null) throw new IllegalArgumentException("通知主体无效");
        if (command.title() == null || command.title().isBlank() || command.content() == null || command.content().isBlank()) {
            throw new IllegalArgumentException("通知内容不能为空");
        }
    }
}
