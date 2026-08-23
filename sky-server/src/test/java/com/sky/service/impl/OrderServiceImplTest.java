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
    void shouldCalculateOrderAmountFromServerCart() {
        AddressBook address = AddressBook.builder().id(3L).userId(7L).consignee("张三")
                .phone("13800138000").build();
        List<ShoppingCart> carts = List.of(
                ShoppingCart.builder().dishId(1L).number(2).amount(new BigDecimal("10.50")).build(),
                ShoppingCart.builder().setmealId(2L).number(1).amount(new BigDecimal("20.00")).build());
        when(addressBookMapper.getById(3L)).thenReturn(address);
        when(deliveryRangeService.fullAddress(address)).thenReturn("北京市海淀区测试地址");
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(carts);
        doAnswer(invocation -> {
            ((Orders) invocation.getArgument(0)).setId(88L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAddressBookId(3L);
        request.setAmount(new BigDecimal("0.01"));
        request.setPackAmount(0);
        request.setDeliveryStatus(1);
        request.setTablewareStatus(1);
        request.setTablewareNumber(3);
        OrderSubmitVO result = orderService.submitOrder(request);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captor.capture());
        verify(deliveryRangeService).check(address);
        assertEquals(new BigDecimal("50.00"), captor.getValue().getAmount());
        assertEquals(3, captor.getValue().getPackAmount());
        assertEquals("北京市海淀区测试地址", captor.getValue().getAddress());
        assertEquals(new BigDecimal("50.00"), result.getOrderAmount());
        verify(shoppingCartMapper).deleteByUserId(7L);
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
        Orders order = Orders.builder().id(20L).userId(7L).status(Orders.TO_BE_CONFIRMED).build();
        when(orderQueryService.getExisting(20L)).thenReturn(order);
        when(orderMapper.updateByExpectedStatus(any(Orders.class), eq(Orders.TO_BE_CONFIRMED))).thenReturn(1);
        OrdersConfirmDTO request = new OrdersConfirmDTO();
        request.setId(20L);

        orderService.confirm(request);

        verify(notificationService).sendStatusAfterCommit(order, Orders.CONFIRMED, "商家已接单");
    }

    @Test
    void shouldRejectConcurrentStatusChangeWithoutNotification() {
        Orders order = Orders.builder().id(20L).status(Orders.TO_BE_CONFIRMED).build();
        when(orderQueryService.getExisting(20L)).thenReturn(order);
        when(orderMapper.updateByExpectedStatus(any(Orders.class), eq(Orders.TO_BE_CONFIRMED))).thenReturn(0);
        OrdersConfirmDTO request = new OrdersConfirmDTO();
        request.setId(20L);

        assertThrows(com.sky.exception.OrderBusinessException.class, () -> orderService.confirm(request));

        verify(notificationService, never()).sendStatusAfterCommit(any(), any(), any());
    }
}
