package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Orders;
import com.sky.entity.OrderDetail;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private UserMapper userMapper;
    @Mock private AddressBookMapper addressBookMapper;
    @Mock private WeChatPayUtil weChatPayUtil;
    @Mock private WeChatProperties weChatProperties;
    @Mock private WebSocketServer webSocketServer;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        ReflectionTestUtils.setField(orderService, "baiduAk", "");
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldCalculateOrderAmountFromServerCart() {
        AddressBook address = AddressBook.builder()
                .id(3L).userId(7L).consignee("张三").phone("13800138000")
                .provinceName("北京市").cityName("北京市").districtName("海淀区").detail("测试地址").build();
        List<ShoppingCart> cart = List.of(
                ShoppingCart.builder().dishId(1L).name("菜品一").number(2).amount(new BigDecimal("10.50")).build(),
                ShoppingCart.builder().setmealId(2L).name("套餐一").number(1).amount(new BigDecimal("20.00")).build());
        when(addressBookMapper.getById(3L)).thenReturn(address);
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(cart);
        doAnswer(invocation -> {
            ((Orders) invocation.getArgument(0)).setId(88L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        OrdersSubmitDTO request = new OrdersSubmitDTO();
        request.setAddressBookId(3L);
        request.setPayMethod(1);
        request.setPackAmount(0);
        request.setAmount(new BigDecimal("0.01"));
        request.setDeliveryStatus(1);
        request.setTablewareStatus(1);
        request.setTablewareNumber(3);

        OrderSubmitVO result = orderService.submitOrder(request);

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals(new BigDecimal("50.00"), captor.getValue().getAmount());
        assertEquals(3, captor.getValue().getPackAmount());
        assertEquals("北京市北京市海淀区测试地址", captor.getValue().getAddress());
        assertEquals(new BigDecimal("50.00"), result.getOrderAmount());
        verify(shoppingCartMapper).deleteByUserId(7L);
    }

    @Test
    void shouldUseDatabaseOrderAmountForWechatPayment() throws Exception {
        Orders order = Orders.builder()
                .id(20L)
                .number("202608220001")
                .userId(7L)
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("50.00"))
                .build();
        User user = User.builder().id(7L).openid("openid-7").build();
        JSONObject paymentResult = new JSONObject();
        paymentResult.put("package", "prepay_id=test");
        when(weChatProperties.getMockPay()).thenReturn(false);
        when(orderMapper.getByNumber("202608220001")).thenReturn(order);
        when(userMapper.getById(7L)).thenReturn(user);
        when(weChatPayUtil.pay("202608220001", new BigDecimal("50.00"),
                "苍穹外卖订单", "openid-7")).thenReturn(paymentResult);

        OrdersPaymentDTO request = new OrdersPaymentDTO();
        request.setOrderNumber("202608220001");
        request.setPayMethod(1);

        OrderPaymentVO result = orderService.payment(request);

        assertEquals("prepay_id=test", result.getPackageStr());
        verify(weChatPayUtil).pay("202608220001", new BigDecimal("50.00"),
                "苍穹外卖订单", "openid-7");
    }

    @Test
    void shouldMergeRepeatedOrderIntoExistingCartLines() {
        Orders order = Orders.builder().id(20L).userId(7L).build();
        OrderDetail existingDetail = OrderDetail.builder()
                .id(1L).orderId(20L).dishId(11L).dishFlavor("微辣").name("宫保鸡丁")
                .number(2).amount(new BigDecimal("28.00")).build();
        OrderDetail newDetail = OrderDetail.builder()
                .id(2L).orderId(20L).setmealId(21L).name("双人套餐")
                .number(1).amount(new BigDecimal("66.00")).build();
        ShoppingCart existingCart = ShoppingCart.builder()
                .id(9L).userId(7L).dishId(11L).dishFlavor("微辣").number(3).build();
        when(orderMapper.getById(20L)).thenReturn(order);
        when(orderDetailMapper.getByOrderId(20L)).thenReturn(List.of(existingDetail, newDetail));
        when(shoppingCartMapper.getOne(any(ShoppingCart.class)))
                .thenReturn(existingCart)
                .thenReturn(null);

        orderService.repetition(20L);

        ArgumentCaptor<ShoppingCart> updateCaptor = ArgumentCaptor.forClass(ShoppingCart.class);
        verify(shoppingCartMapper).updateNumber(updateCaptor.capture());
        assertEquals(5, updateCaptor.getValue().getNumber());

        ArgumentCaptor<ShoppingCart> insertCaptor = ArgumentCaptor.forClass(ShoppingCart.class);
        verify(shoppingCartMapper).insert(insertCaptor.capture());
        assertEquals(7L, insertCaptor.getValue().getUserId());
        assertEquals(21L, insertCaptor.getValue().getSetmealId());
        assertEquals(1, insertCaptor.getValue().getNumber());
        verify(shoppingCartMapper, never()).insertBatch(any());
    }

    @Test
    void shouldPushConfirmedStatusToOrderOwner() {
        Orders order = Orders.builder()
                .id(20L)
                .userId(7L)
                .number("202608220001")
                .status(Orders.TO_BE_CONFIRMED)
                .build();
        when(orderMapper.getById(20L)).thenReturn(order);
        OrdersConfirmDTO request = new OrdersConfirmDTO();
        request.setId(20L);

        orderService.confirm(request);

        verify(webSocketServer).sendOrderStatusToUser(
                7L, 20L, Orders.CONFIRMED, "商家已接单");
    }
}
