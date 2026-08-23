package com.sky.service.order;

import com.alibaba.fastjson.JSONObject;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private UserMapper userMapper;
    @Mock private WeChatPayUtil weChatPayUtil;
    @Mock private WeChatProperties weChatProperties;
    @Mock private OrderNotificationService notificationService;
    @InjectMocks private OrderPaymentService paymentService;

    @Test
    void shouldUseDatabaseOrderAmountForWechatPayment() throws Exception {
        Orders order = Orders.builder().id(20L).number("202608220001").userId(7L)
                .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("50.00")).build();
        when(orderMapper.getByNumber("202608220001")).thenReturn(order);
        when(userMapper.getById(7L)).thenReturn(User.builder().id(7L).openid("openid-7").build());
        JSONObject response = new JSONObject();
        response.put("package", "prepay_id=test");
        when(weChatPayUtil.pay("202608220001", new BigDecimal("50.00"),
                "苍穹外卖订单", "openid-7")).thenReturn(response);
        OrdersPaymentDTO request = new OrdersPaymentDTO();
        request.setOrderNumber("202608220001");

        OrderPaymentVO result = paymentService.payment(request, 7L);

        assertEquals("prepay_id=test", result.getPackageStr());
        verify(weChatPayUtil).pay("202608220001", new BigDecimal("50.00"),
                "苍穹外卖订单", "openid-7");
    }
}
