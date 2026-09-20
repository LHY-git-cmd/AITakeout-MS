package com.sky.service.notification;

import com.sky.entity.UserNotification;
import com.sky.mapper.OutboxEventMapper;
import com.sky.mapper.UserNotificationMapper;
import com.sky.service.notification.model.NotificationModels.NotificationCommand;
import com.sky.service.notification.model.NotificationModels.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证实时连接之外仍有持久化通知和 Outbox 可供补拉。 */
class NotificationOutboxTest {
    @Test
    void recordPersistsNotificationAndOutboxTogether() {
        UserNotificationMapper notifications = mock(UserNotificationMapper.class);
        OutboxEventMapper outbox = mock(OutboxEventMapper.class);
        doAnswer(invocation -> {
            UserNotification notification = invocation.getArgument(0);
            notification.setId(11L);
            return 1;
        }).when(notifications).insert(any());
        NotificationService service = new NotificationService(notifications, outbox);

        UserNotification saved = service.record(new NotificationCommand("ORDER:7:ACCEPTED", 9L,
                NotificationType.ORDER, "商家已接单", "订单正在制作", 7L));

        assertThat(saved.getId()).isEqualTo(11L);
        verify(notifications).insert(any());
        verify(outbox).insert(argThat(event -> event.getPayloadJson().contains("notificationId")
                && "PENDING".equals(event.getStatus())));
    }
}
