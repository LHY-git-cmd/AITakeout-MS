package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 用户通知补拉时间参数的 HTTP 契约。 */
class NotificationControllerTest {
    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void acceptsMinutePrecisionTimestampUsedByNotificationClient() throws Exception {
        NotificationService service = mock(NotificationService.class);
        LocalDateTime since = LocalDateTime.of(2026, 9, 21, 5, 5);
        when(service.list(7L, null, since, 20)).thenReturn(List.of());
        BaseContext.setCurrentId(7L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service)).build();

        mvc.perform(get("/user/notifications").param("since", "2026-09-21 05:05").param("limit", "20"))
                .andExpect(status().isOk());

        verify(service).list(7L, null, since, 20);
    }
}
