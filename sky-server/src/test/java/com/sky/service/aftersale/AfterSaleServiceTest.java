package com.sky.service.aftersale;

import com.sky.dto.AfterSaleApplyDTO;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AfterSaleRequestMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.RefundTransactionMapper;
import com.sky.service.notification.NotificationService;
import com.sky.service.order.OrderTimelineService;
import com.sky.service.payment.RefundApplicationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 精确覆盖完成订单的 24 小时售后边界。 */
class AfterSaleServiceTest {
    @Test
    void completedOrderAtExactlyTwentyFourHoursIsExpired() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);
        OrderMapper orders = mock(OrderMapper.class);
        Orders order = Orders.builder().id(7L).userId(9L).status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusHours(24)).build();
        when(orders.getByIdForUpdate(7L)).thenReturn(order);
        AfterSaleService service = new AfterSaleService(mock(AfterSaleRequestMapper.class), orders,
                mock(RefundTransactionMapper.class), mock(RefundApplicationService.class),
                mock(OrderTimelineService.class), mock(NotificationService.class), clock);
        AfterSaleApplyDTO dto = new AfterSaleApplyDTO();
        dto.setReason("餐品存在问题");

        assertThatThrownBy(() -> service.apply(9L, 7L, dto, "boundary"))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessage("售后申请已超过24小时");
    }
}
