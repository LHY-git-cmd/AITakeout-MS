package com.sky.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.AgentToolOperationRequest;
import com.sky.entity.ShoppingCart;
import com.sky.service.*;
import com.sky.service.aftersale.AfterSaleService;
import com.sky.service.catalog.ProductSearchService;
import com.sky.service.order.OrderTimelineService;
import com.sky.vo.AgentToolOperationResponse;
import com.sky.vo.OrderVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAgentToolExecutorTest {
    private final ProductSearchService searchService = mock(ProductSearchService.class);
    private final ShoppingCartService cartService = mock(ShoppingCartService.class);
    private final OrderService orderService = mock(OrderService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserAgentToolExecutor executor = new UserAgentToolExecutor(
            searchService, mock(DishService.class), mock(SetmealService.class), cartService,
            orderService, mock(OrderTimelineService.class),
            mock(AfterSaleService.class), redis, objectMapper);

    @Test
    void rejectsAdminOperationForUserActor() throws Exception {
        AgentToolOperationResponse response = executor.execute(request(
                "order.detail", "{\"order_id\":1}"), 17L, "trace-1");

        assertEquals("rejected", response.status());
        assertEquals("TOOL_PERMISSION_DENIED", response.error().get("code"));
    }

    @Test
    void addCartUsesTaskToolIdempotencyAndRemovesUserIdFromResult() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
        when(cartService.showShoppingCart()).thenReturn(List.of(ShoppingCart.builder()
                .id(3L).userId(17L).dishId(8L).name("清蒸鱼").number(2)
                .amount(new BigDecimal("28.00")).build()));

        AgentToolOperationResponse response = executor.execute(request(
                "user.cart.add", "{\"dish_id\":8,\"quantity\":2}"), 17L, "trace-1");

        assertEquals("success", response.status());
        verify(cartService, times(2)).addShoppingCart(argThat(value -> value.getDishId().equals(8L)));
        assertFalse(objectMapper.writeValueAsString(response.data()).contains("userId"));
        verify(values).set(anyString(), eq("COMPLETED"), eq(Duration.ofHours(24)));
    }

    @Test
    void orderDataRedactsPersonalContactFieldsBeforeAgentSeesIt() throws Exception {
        OrderVO order = new OrderVO();
        order.setId(7L);
        order.setNumber("ORDER-7");
        order.setStatus(4);
        order.setUserId(7L);
        order.setPhone("13800138000");
        order.setAddress("测试路1号");
        when(orderService.detailsForUser(7L)).thenReturn(order);

        AgentToolOperationResponse response = executor.execute(request(
                "user.order.detail", "{\"order_id\":7}"), 17L, "trace-1");

        String safe = objectMapper.writeValueAsString(response.data());
        assertEquals("success", response.status());
        assertTrue(safe.contains("ORDER-7"));
        assertFalse(safe.contains("13800138000"));
        assertFalse(safe.contains("测试路1号"));
        assertFalse(safe.contains("userId"));
    }

    private AgentToolOperationRequest request(String operation, String arguments) throws Exception {
        return new AgentToolOperationRequest("request-1", "task-1", "call-1", operation,
                objectMapper.readTree(arguments));
    }
}
