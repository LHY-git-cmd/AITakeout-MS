package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.properties.OrderTaskProperties;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class OrderTaskTest {

    @Mock private OrderMapper orderMapper;
    @Mock private WebSocketServer webSocketServer;
    @Mock private OrderTaskProperties orderTaskProperties;
    @InjectMocks private OrderTask orderTask;

    @Test
    void shouldBatchUpdateTimeoutOrdersOnce() {
        Orders first = Orders.builder().id(1L).userId(2L).status(Orders.PENDING_PAYMENT).build();
        Orders second = Orders.builder().id(2L).userId(3L).status(Orders.PENDING_PAYMENT).build();
        when(orderTaskProperties.getBatchSize()).thenReturn(200);
        when(orderMapper.getBatchForUpdate(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of(first, second));
        when(orderMapper.updateBatchByExpectedStatus(any(Orders.class), eq(List.of(1L, 2L)),
                eq(Orders.PENDING_PAYMENT))).thenReturn(2);

        orderTask.processTimeoutOrder();

        verify(orderMapper, times(1)).updateBatchByExpectedStatus(
                any(Orders.class), eq(List.of(1L, 2L)), eq(Orders.PENDING_PAYMENT));
        verify(webSocketServer).sendOrderStatusToUser(2L, 1L, Orders.CANCELLED, "订单超时，已自动取消");
        verify(webSocketServer).sendOrderStatusToUser(3L, 2L, Orders.CANCELLED, "订单超时，已自动取消");
    }

    @Test
    void shouldSkipBatchUpdateWhenNoTimeoutOrderExists() {
        when(orderTaskProperties.getBatchSize()).thenReturn(200);
        when(orderMapper.getBatchForUpdate(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of());

        orderTask.processTimeoutOrder();

        verify(orderMapper, never()).updateBatchByExpectedStatus(any(), any(), any());
        verify(webSocketServer, never()).sendOrderStatusToUser(any(), any(), any(), any());
    }

    @Test
    void shouldRollbackWhenBatchUpdateCountDoesNotMatch() {
        Orders order = Orders.builder().id(1L).userId(2L).status(Orders.PENDING_PAYMENT).build();
        when(orderTaskProperties.getBatchSize()).thenReturn(200);
        when(orderMapper.getBatchForUpdate(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of(order));
        when(orderMapper.updateBatchByExpectedStatus(any(Orders.class), eq(List.of(1L)),
                eq(Orders.PENDING_PAYMENT))).thenReturn(0);

        assertThrows(IllegalStateException.class, orderTask::processTimeoutOrder);

        verify(webSocketServer, never()).sendOrderStatusToUser(any(), any(), any(), any());
    }
}
