package com.sky.service.aftersale;

import com.sky.dto.AfterSaleApplyDTO;
import com.sky.entity.AfterSaleRequest;
import com.sky.entity.Orders;
import com.sky.entity.RefundTransaction;
import com.sky.mapper.AfterSaleRequestMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.RefundTransactionMapper;
import com.sky.service.notification.NotificationService;
import com.sky.service.order.OrderTimelineService;
import com.sky.service.payment.RefundApplicationService;
import com.sky.service.payment.model.RefundModels.RefundStatus;
import com.sky.service.payment.model.RefundModels.RefundView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验收已支付待接单订单从取消申请到全额退款结果的业务编排。 */
class UserOrderLifecycleAcceptanceTest {
    @Test
    void paidOrderCancellationReturnsAuthoritativePaidAmount() {
        AfterSaleRequestMapper requests = mock(AfterSaleRequestMapper.class);
        OrderMapper orders = mock(OrderMapper.class);
        RefundTransactionMapper refunds = mock(RefundTransactionMapper.class);
        RefundApplicationService refundService = mock(RefundApplicationService.class);
        Orders order = Orders.builder().id(12L).number("ORDER-12").userId(7L)
                .status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID).amountCent(1_800L).build();
        when(orders.getByIdForUpdate(12L)).thenReturn(order);
        when(orders.updateByExpectedStatus(any(), eq(Orders.TO_BE_CONFIRMED))).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            AfterSaleRequest request = invocation.getArgument(0);
            request.setId(3L);
            return 1;
        }).when(requests).insert(any());
        RefundView refund = new RefundView("REF-12", 12L, 1_800L, RefundStatus.SUCCEEDED,
                1, null, null, java.time.LocalDateTime.now(), null);
        when(refundService.createFullRefund(eq(12L), any())).thenReturn(refund);
        when(requests.findById(3L)).thenReturn(AfterSaleRequest.builder().id(3L).requestNo("AS-12")
                .orderId(12L).userId(7L).requestType("CANCELLATION").reason("行程变化")
                .status("COMPLETED").refundNo("REF-12").build());
        when(refunds.findByRefundNo("REF-12")).thenReturn(RefundTransaction.builder()
                .refundNo("REF-12").orderId(12L).userId(7L).amountCent(1_800L).status("SUCCEEDED").build());
        AfterSaleService service = new AfterSaleService(requests, orders, refunds, refundService,
                mock(OrderTimelineService.class), mock(NotificationService.class),
                Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC));
        AfterSaleApplyDTO dto = new AfterSaleApplyDTO();
        dto.setReason("行程变化");

        var result = service.apply(7L, 12L, dto, "cancel-once");

        verify(refundService).createFullRefund(eq(12L), org.mockito.ArgumentMatchers.startsWith("AFTER_SALE:"));
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.refundAmountCent()).isEqualTo(1_800L);
        assertThat(result.refundStatus()).isEqualTo("SUCCEEDED");
    }
}
