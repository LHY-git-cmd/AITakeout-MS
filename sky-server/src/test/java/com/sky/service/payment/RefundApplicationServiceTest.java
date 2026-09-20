package com.sky.service.payment;

import com.sky.entity.MockAccount;
import com.sky.entity.Orders;
import com.sky.entity.PaymentTransaction;
import com.sky.entity.RefundTransaction;
import com.sky.mapper.*;
import com.sky.service.account.LedgerTransferService;
import com.sky.service.account.model.AccountModels.FreezeResult;
import com.sky.service.notification.NotificationService;
import com.sky.service.order.OrderTimelineService;
import com.sky.service.payment.model.RefundModels.RefundGatewayResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证退款失败只保留冻结资金，重试不会二次冻结。 */
class RefundApplicationServiceTest {
    @Test
    void failedRefundKeepsOriginalFreezeForRetry() {
        RefundTransactionMapper refunds = mock(RefundTransactionMapper.class);
        PaymentTransactionMapper payments = mock(PaymentTransactionMapper.class);
        OrderMapper orders = mock(OrderMapper.class);
        MockAccountMapper accounts = mock(MockAccountMapper.class);
        LedgerTransferService ledger = mock(LedgerTransferService.class);
        RefundGateway gateway = mock(RefundGateway.class);
        Orders order = Orders.builder().id(7L).userId(9L).status(Orders.CANCELLED).payStatus(Orders.PAID).build();
        PaymentTransaction payment = PaymentTransaction.builder().id(3L).paymentNo("PAY-7").amountCent(8_800L).build();
        MockAccount source = MockAccount.builder().id(1L).accountType("PLATFORM_PENDING").availableCent(8_800L).frozenCent(0L).build();
        MockAccount target = MockAccount.builder().id(2L).accountType("USER").ownerId(9L).availableCent(0L).frozenCent(0L).build();
        when(orders.getByIdForUpdate(7L)).thenReturn(order);
        when(payments.findLatestByOrderAndStatus(7L, "SUCCEEDED")).thenReturn(payment);
        when(accounts.findByTypeAndOwner("PLATFORM_PENDING", 0L)).thenReturn(source);
        when(accounts.findByTypeAndOwner("USER", 9L)).thenReturn(target);
        when(ledger.freeze(any())).thenReturn(new FreezeResult(10L, "TR-FREEZE", 1L, 8_800L, 0L, 8_800L, false));
        when(refunds.updateState(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        doAnswer(invocation -> { RefundTransaction value = invocation.getArgument(0); value.setId(5L); return 1; }).when(refunds).insert(any());
        RefundTransaction processing = RefundTransaction.builder().id(5L).refundNo("REF-7").businessKey("CANCEL-7")
                .orderId(7L).paymentId(3L).userId(9L).sourceAccountId(1L).targetAccountId(2L)
                .amountCent(8_800L).status("PROCESSING").attemptCount(1).build();
        RefundTransaction failed = RefundTransaction.builder().id(5L).refundNo("REF-7").orderId(7L).userId(9L)
                .amountCent(8_800L).status("FAILED").attemptCount(1).failureCode("MOCK_FAILED").build();
        when(refunds.findByRefundNoForUpdate(any())).thenReturn(processing);
        when(refunds.findByRefundNo(any())).thenReturn(failed);
        when(gateway.refund(any(), any(), anyLong())).thenReturn(new RefundGatewayResult(false, null, "MOCK_FAILED", "模拟失败"));
        RefundApplicationService service = new RefundApplicationService(refunds, mock(AfterSaleRequestMapper.class), payments,
                orders, accounts, ledger, gateway, mock(OrderTimelineService.class), mock(NotificationService.class));

        var result = service.createFullRefund(7L, "CANCEL-7");

        assertThat(result.status().name()).isEqualTo("FAILED");
        verify(ledger, times(1)).freeze(any());
        verify(ledger, never()).transferFrozen(any());
    }
}
