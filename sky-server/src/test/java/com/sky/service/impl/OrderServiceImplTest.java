package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.order.DeliveryRangeService;
import com.sky.service.order.OrderNotificationService;
import com.sky.service.order.OrderPaymentService;
import com.sky.service.order.OrderQueryService;
import com.sky.service.order.OrderStateMachine;
import com.sky.service.order.model.OrderTransition;
import com.sky.service.aftersale.AfterSaleService;
import com.sky.vo.OrderSubmitVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private AddressBookMapper addressBookMapper;
    @Mock private OrderQueryService orderQueryService;
    @Mock private OrderPaymentService orderPaymentService;
    @Mock private DeliveryRangeService deliveryRangeService;
    @Mock private OrderNotificationService notificationService;
    @Mock private OrderStateMachine orderStateMachine;
    @Mock private AfterSaleService afterSaleService;
    @InjectMocks private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldRequireAuthoritativePreviewBeforeSubmit() {
        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAddressBookId(3L);
        request.setDeliveryStatus(1);
        request.setTablewareStatus(1);
        request.setTablewareNumber(3);

        assertThrows(com.sky.exception.OrderBusinessException.class,
                () -> orderService.submitOrder(request, "SUBMIT-1"));
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void shouldMergeRepeatedOrderIntoExistingCartLines() {
        Orders order = Orders.builder().id(20L).userId(7L).build();
        OrderDetail existingDetail = OrderDetail.builder().orderId(20L).dishId(11L)
                .dishFlavor("微辣").number(2).build();
        OrderDetail newDetail = OrderDetail.builder().orderId(20L).setmealId(21L).number(1).build();
        ShoppingCart existingCart = ShoppingCart.builder().id(9L).number(3).build();
        when(orderQueryService.getExisting(20L)).thenReturn(order);
        when(orderDetailMapper.getByOrderId(20L)).thenReturn(List.of(existingDetail, newDetail));
        when(shoppingCartMapper.getOne(any(ShoppingCart.class))).thenReturn(existingCart).thenReturn(null);

        orderService.repetition(20L);

        ArgumentCaptor<ShoppingCart> updateCaptor = ArgumentCaptor.forClass(ShoppingCart.class);
        verify(shoppingCartMapper).updateNumber(updateCaptor.capture());
        assertEquals(5, updateCaptor.getValue().getNumber());
        ArgumentCaptor<ShoppingCart> insertCaptor = ArgumentCaptor.forClass(ShoppingCart.class);
        verify(shoppingCartMapper).insert(insertCaptor.capture());
        assertEquals(7L, insertCaptor.getValue().getUserId());
        assertEquals(21L, insertCaptor.getValue().getSetmealId());
    }

    @Test
    void shouldPushConfirmedStatusThroughNotificationService() {
        OrdersConfirmDTO request = new OrdersConfirmDTO();
        request.setId(20L);

        orderService.confirm(request);

        verify(orderStateMachine).transition(any(OrderTransition.class));
    }

    @Test
    void shouldRejectConcurrentStatusChangeWithoutNotification() {
        org.mockito.Mockito.doThrow(new com.sky.exception.OrderBusinessException("订单状态已变化，请刷新后重试"))
                .when(orderStateMachine).transition(any(OrderTransition.class));
        OrdersConfirmDTO request = new OrdersConfirmDTO();
        request.setId(20L);

        assertThrows(com.sky.exception.OrderBusinessException.class, () -> orderService.confirm(request));

        verify(notificationService, never()).sendStatusAfterCommit(any(), any(), any());
    }

    @Test
    void shouldRejectCancellationWhilePaymentIsProcessing() throws Exception {
        Orders order = Orders.builder().id(20L).userId(7L).status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID).build();
        when(orderMapper.getByIdForUpdate(20L)).thenReturn(order);
        org.mockito.Mockito.doThrow(new com.sky.exception.OrderBusinessException("支付处理中，暂不能取消订单"))
                .when(orderPaymentService).assertCancelable(20L);

        assertThrows(com.sky.exception.OrderBusinessException.class, () -> orderService.userCancelById(20L));

        verify(orderMapper, never()).updateByExpectedStatus(any(), any());
        verify(orderMapper).getByIdForUpdate(20L);
    }
}
