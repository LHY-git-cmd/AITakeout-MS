package com.sky.service.order;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.result.PageResult;
import com.sky.vo.OrderVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @InjectMocks private OrderQueryService queryService;

    @Test
    void shouldBatchLoadDetailsForAdminOrderPage() {
        Page<Orders> page = new Page<>(1, 10);
        page.add(Orders.builder().id(10L).build());
        page.add(Orders.builder().id(11L).build());
        when(orderMapper.pageQuery(any(OrdersPageQueryDTO.class))).thenReturn(page);
        when(orderDetailMapper.getByOrderIds(List.of(10L, 11L))).thenReturn(List.of(
                OrderDetail.builder().orderId(10L).name("菜品一").number(2).build(),
                OrderDetail.builder().orderId(11L).name("套餐一").number(1).build()));
        OrdersPageQueryDTO request = new OrdersPageQueryDTO();
        request.setPage(1);
        request.setPageSize(10);

        PageResult result = queryService.page(request, false);

        verify(orderDetailMapper, times(1)).getByOrderIds(List.of(10L, 11L));
        verify(orderDetailMapper, never()).getByOrderId(any());
        List<OrderVO> records = result.getRecords();
        assertEquals("菜品一*2;", records.get(0).getOrderDishes());
        assertEquals("套餐一*1;", records.get(1).getOrderDishes());
        assertNull(records.get(0).getOrderDetailList());
    }

    @Test
    void shouldSkipDetailQueryForEmptyOrderPage() {
        when(orderMapper.pageQuery(any(OrdersPageQueryDTO.class))).thenReturn(new Page<>(1, 10));
        OrdersPageQueryDTO request = new OrdersPageQueryDTO();
        request.setPage(1);
        request.setPageSize(10);

        PageResult result = queryService.page(request, false);

        assertEquals(0, result.getRecords().size());
        verify(orderDetailMapper, never()).getByOrderIds(any());
    }
}
