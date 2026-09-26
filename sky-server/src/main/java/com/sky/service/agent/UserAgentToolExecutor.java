package com.sky.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.context.BaseContext;
import com.sky.dto.AgentToolOperationRequest;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.service.DishService;
import com.sky.service.OrderService;
import com.sky.service.SetmealService;
import com.sky.service.ShoppingCartService;
import com.sky.service.aftersale.AfterSaleService;
import com.sky.service.catalog.ProductSearchService;
import com.sky.service.order.OrderTimelineService;
import com.sky.vo.AgentToolOperationResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 普通用户工具执行器。身份从已持久化任务读取，Python参数不能覆盖用户ID。
 */
@Service
public class UserAgentToolExecutor {
    private static final Set<String> OPERATIONS = Set.of(
            "shop.status.get", "user.product.search", "user.product.detail",
            "user.cart.get", "user.cart.add", "user.order.list", "user.order.detail",
            "user.order.timeline", "user.after_sale.status");

    private final ProductSearchService productSearchService;
    private final DishService dishService;
    private final SetmealService setmealService;
    private final ShoppingCartService shoppingCartService;
    private final OrderService orderService;
    private final OrderTimelineService timelineService;
    private final AfterSaleService afterSaleService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /** 唯一构造器由Spring自动注入，避免多构造器导致Bean实例化歧义。 */
    public UserAgentToolExecutor(ProductSearchService productSearchService, DishService dishService,
                                 SetmealService setmealService, ShoppingCartService shoppingCartService,
                                 OrderService orderService, OrderTimelineService timelineService,
                                 AfterSaleService afterSaleService, RedisTemplate<String, Object> redisTemplate,
                                 ObjectMapper objectMapper) {
        this.productSearchService = productSearchService;
        this.dishService = dishService;
        this.setmealService = setmealService;
        this.shoppingCartService = shoppingCartService;
        this.orderService = orderService;
        this.timelineService = timelineService;
        this.afterSaleService = afterSaleService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean supports(String operation) {
        return OPERATIONS.contains(operation);
    }

    /** 在用户上下文中执行经过白名单和参数边界验证的业务操作。 */
    public AgentToolOperationResponse execute(AgentToolOperationRequest request, long userId,
                                              String traceId) {
        if (!supports(request.operation())) {
            return AgentToolOperationResponse.error(request.toolCallId(), "rejected",
                    "TOOL_PERMISSION_DENIED", "当前用户无权调用该工具", traceId);
        }
        Long previousId = BaseContext.getCurrentId();
        String previousRole = BaseContext.getCurrentRole();
        try {
            BaseContext.setCurrentId(userId);
            BaseContext.setCurrentRole(null);
            Object data = sanitize(dispatch(request, userId));
            return AgentToolOperationResponse.success(request.toolCallId(), data, traceId);
        } finally {
            BaseContext.removeCurrentId();
            if (previousId != null) BaseContext.setCurrentId(previousId);
            if (previousRole != null) BaseContext.setCurrentRole(previousRole);
        }
    }

    private Object dispatch(AgentToolOperationRequest request, long userId) {
        JsonNode args = request.arguments();
        return switch (request.operation()) {
            case "shop.status.get" -> Map.of("status", shopStatus());
            case "user.product.search" -> productSearchService.search(
                    requiredText(args, "keyword"), null, optionalInt(args, "limit", 10, 1, 20));
            case "user.product.detail" -> productDetail(args);
            case "user.cart.get" -> safeCart(shoppingCartService.showShoppingCart());
            case "user.cart.add" -> addCart(request, args, userId);
            case "user.order.list" -> orderService.pageQuery4User(
                    optionalInt(args, "page", 1, 1, 1000),
                    optionalInt(args, "page_size", 10, 1, 20), optionalNullableInt(args, "status", 1, 7));
            case "user.order.detail" -> orderService.detailsForUser(requiredLong(args, "order_id"));
            case "user.order.timeline" -> timelineService.timeline(userId, requiredLong(args, "order_id"));
            case "user.after_sale.status" -> afterSaleService.getForUser(userId, requiredLong(args, "order_id"));
            default -> throw new IllegalArgumentException("不支持的用户工具");
        };
    }

    private Object productDetail(JsonNode args) {
        String type = requiredText(args, "product_type");
        long id = requiredLong(args, "product_id");
        return switch (type) {
            case "dish" -> dishService.getById(id);
            case "setmeal" -> setmealService.getByIdWithDish(id);
            default -> throw new IllegalArgumentException("product_type必须是dish或setmeal");
        };
    }

    /** 低风险加购使用tool_call_id做幂等保护，失败时释放幂等键以允许重试。 */
    private Object addCart(AgentToolOperationRequest request, JsonNode args, long userId) {
        String key = "agent:user-tool:" + userId + ":" + request.taskId() + ":" + request.toolCallId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", Duration.ofMinutes(30));
        if (Boolean.FALSE.equals(acquired)) {
            return Map.of("replayed", true, "items", safeCart(shoppingCartService.showShoppingCart()));
        }
        try {
            ShoppingCartDTO dto = new ShoppingCartDTO();
            dto.setDishId(optionalLong(args, "dish_id"));
            dto.setSetmealId(optionalLong(args, "setmeal_id"));
            dto.setDishFlavor(optionalText(args, "dish_flavor"));
            if (!dto.isTargetValid()) throw new IllegalArgumentException("菜品和套餐必须且只能选择一个");
            int quantity = optionalInt(args, "quantity", 1, 1, 20);
            for (int index = 0; index < quantity; index++) {
                shoppingCartService.addShoppingCart(dto);
            }
            redisTemplate.opsForValue().set(key, "COMPLETED", Duration.ofHours(24));
            return Map.of("replayed", false, "items", safeCart(shoppingCartService.showShoppingCart()));
        } catch (RuntimeException exception) {
            redisTemplate.delete(key);
            throw exception;
        }
    }

    /** 移除购物车实体中的userId，避免主体字段出现在模型上下文。 */
    private List<Map<String, Object>> safeCart(List<ShoppingCart> items) {
        return items.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.getId()); value.put("name", item.getName());
            value.put("dish_id", item.getDishId()); value.put("setmeal_id", item.getSetmealId());
            value.put("dish_flavor", item.getDishFlavor()); value.put("quantity", item.getNumber());
            value.put("amount", item.getAmount()); value.put("image", item.getImage());
            return value;
        }).toList();
    }

    private int shopStatus() {
        Object value = redisTemplate.opsForValue().get("SHOP_STATUS");
        return value instanceof Number number ? number.intValue() : 1;
    }

    /** 业务服务返回的订单对象可能带有联系方式，用户Agent只需状态和商品事实。 */
    private Object sanitize(Object value) {
        JsonNode node = objectMapper.valueToTree(value);
        redact(node);
        return objectMapper.convertValue(node, Object.class);
    }

    private void redact(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (String field : Set.of("userId", "user_id", "phone", "address", "consignee",
                    "userName", "username", "addressBookId", "address_book_id",
                    "addressLatitude", "addressLongitude", "address_latitude", "address_longitude")) {
                object.remove(field);
            }
            object.elements().forEachRemaining(this::redact);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::redact);
        }
    }

    private String requiredText(JsonNode args, String name) {
        String value = optionalText(args, name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }

    private String optionalText(JsonNode args, String name) {
        JsonNode value = args == null ? null : args.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private long requiredLong(JsonNode args, String name) {
        Long value = optionalLong(args, name);
        if (value == null || value <= 0) throw new IllegalArgumentException(name + "必须大于0");
        return value;
    }

    private Long optionalLong(JsonNode args, String name) {
        JsonNode value = args == null ? null : args.get(name);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private int optionalInt(JsonNode args, String name, int fallback, int min, int max) {
        Integer value = optionalNullableInt(args, name, min, max);
        return value == null ? fallback : value;
    }

    private Integer optionalNullableInt(JsonNode args, String name, int min, int max) {
        JsonNode value = args == null ? null : args.get(name);
        if (value == null || value.isNull()) return null;
        int parsed = value.asInt();
        if (parsed < min || parsed > max) throw new IllegalArgumentException(name + "超出允许范围");
        return parsed;
    }
}
