package com.sky.service.order;

import com.sky.entity.AfterSaleRequest;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AfterSaleRequestMapper;
import com.sky.mapper.MockAccountMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.account.LedgerTransferService;
import com.sky.service.notification.NotificationService;
import com.sky.service.order.model.OrderTransition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 验证待审核申请会阻断继续履约。 */
class OrderStateMachineTest {
    @Test
    void cannotDeliverWhileCancellationIsPending() {
        OrderMapper orders = mock(OrderMapper.class);
        AfterSaleRequestMapper afterSales = mock(AfterSaleRequestMapper.class);
        when(orders.getByIdForUpdate(7L)).thenReturn(Orders.builder().id(7L).number("O7").userId(9L)
                .status(Orders.CONFIRMED).payStatus(Orders.PAID).build());
        when(afterSales.findActiveByOrderId(7L)).thenReturn(AfterSaleRequest.builder().id(3L).status("PENDING").build());
        OrderStateMachine machine = new OrderStateMachine(orders, afterSales, mock(MockAccountMapper.class),
                mock(LedgerTransferService.class), mock(OrderTimelineService.class), mock(NotificationService.class));

        assertThatThrownBy(() -> machine.transition(new OrderTransition(7L, Orders.CONFIRMED,
                Orders.DELIVERY_IN_PROGRESS, "ADMIN", 1L, "开始配送")))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining("取消申请处理中");
        verify(orders, never()).updateByExpectedStatus(any(), any());
    }
}
