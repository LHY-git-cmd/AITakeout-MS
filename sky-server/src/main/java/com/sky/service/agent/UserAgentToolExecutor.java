package com.sky.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.context.BaseContext;
import com.sky.dto.AgentToolOperationRequest;
import com.sky.dto.ShoppingCartDTO;
import com.sky.dto.DietRecommendationDTO;
import com.sky.dto.AfterSaleApplyDTO;
import com.sky.entity.ShoppingCart;
import com.sky.service.DishService;
import com.sky.service.OrderService;
import com.sky.service.SetmealService;
import com.sky.service.ShoppingCartService;
import com.sky.service.aftersale.AfterSaleService;
import com.sky.service.catalog.ProductSearchService;
import com.sky.service.diet.DietRecommendationService;
import com.sky.service.order.OrderTimelineService;
import com.sky.vo.AgentToolOperationResponse;
import com.sky.properties.AgentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 普通用户工具执行器。身份从已持久化任务读取，Python参数不能覆盖用户ID。
 */
@Service
public class UserAgentToolExecutor {
    private static final Set<String> OPERATIONS = Set.of(
            "shop.status.get", "user.product.search", "user.product.detail",
            "user.cart.get", "user.cart.add", "user.order.list", "user.order.detail",
            "user.order.timeline", "user.after_sale.status", "user.order.action.preview",
            "user.order.remind", "user.order.cancel.request", "user.after_sale.submit",
            "user.diet.profile.get", "user.diet.recommend");
    private static final Set<String> CONFIRMED_OPERATIONS = Set.of(
            "user.order.cancel.request", "user.after_sale.submit");

    private final ProductSearchService productSearchService;
    private final DishService dishService;
    private final SetmealService setmealService;
    private final ShoppingCartService shoppingCartService;
    private final OrderService orderService;
    private final OrderTimelineService timelineService;
    private final AfterSaleService afterSaleService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private AgentProperties properties;
    private DietRecommendationService dietRecommendationService;

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

    @Autowired
    public void setProperties(AgentProperties properties) { this.properties = properties; }

    @Autowired
    public void setDietRecommendationService(DietRecommendationService value) {
        this.dietRecommendationService = value;
    }

    public boolean requiresConfirmation(String operation) {
        return CONFIRMED_OPERATIONS.contains(operation);
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
            if (properties != null && !featureEnabled(request.operation())) {
                return AgentToolOperationResponse.error(request.toolCallId(), "rejected",
                        "FEATURE_DISABLED", "该用户端能力当前未开放", traceId);
            }
            if (requiresConfirmation(request.operation())) {
                return AgentToolOperationResponse.error(request.toolCallId(), "confirmation_required",
                        "CONFIRMATION_REQUIRED", "该操作必须先由用户确认", traceId);
            }
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

    private boolean featureEnabled(String operation) {
        return switch (operation) {
            case "user.order.remind" -> properties.isUserAgentReminderEnabled();
            case "user.order.cancel.request" -> properties.isUserAgentCancellationEnabled();
            case "user.after_sale.submit" -> properties.isUserAgentAfterSaleEnabled();
            default -> true;
        };
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
            case "user.order.action.preview" -> previewOrderAction(args);
            case "user.order.remind" -> remindOrder(request, args, userId);
            case "user.diet.profile.get" -> dietRecommendationService.getProfile(userId);
            case "user.diet.recommend" -> recommendDiet(request, args, userId);
            default -> throw new IllegalArgumentException("不支持的用户工具");
        };
    }

    /** 将模型参数收敛为受控 DTO，再交给确定性推荐服务处理。 */
    private Object recommendDiet(AgentToolOperationRequest request, JsonNode args, long userId) {
        DietRecommendationDTO dto = new DietRecommendationDTO();
        dto.setScene(requiredText(args, "scene"));
        dto.setPeopleCount(optionalInt(args, "people_count", 1, 1, 20));
        if (args != null && args.hasNonNull("budget")) {
            dto.setBudget(args.get("budget").decimalValue());
        }
        dto.setMealType(optionalText(args, "meal_type"));
        dto.setRegionCode(optionalText(args, "region_code"));
        dto.setUseSavedProfile(args == null || !args.has("use_saved_profile")
                || args.get("use_saved_profile").asBoolean());
        dto.setAllergens(stringList(args, "allergens"));
        dto.setExcludedIngredients(stringList(args, "excluded_ingredients"));
        dto.setGoals(stringList(args, "goals"));
        dto.setConditions(stringList(args, "conditions"));
        dto.setPreferences(stringList(args, "preferences"));
        dto.setHardConstraints(stringList(args, "hard_constraints"));
        dto.setSoftPreferences(stringList(args, "soft_preferences"));
        dto.setSeason(optionalText(args, "season"));
        if (args != null && args.hasNonNull("confidence")) dto.setConfidence(args.get("confidence").decimalValue());
        dto.setLimit(optionalInt(args, "limit", 5, 1, 20));
        String idempotencyKey = hash(request.taskId() + ":" + request.toolCallId());
        return dietRecommendationService.recommend(userId, idempotencyKey, dto, null);
    }

    /** 返回确认卡需要的影响范围以及稳定资源版本。 */
    public Map<String, Object> previewConfirmedOperation(AgentToolOperationRequest request,
                                                         long userId) {
        if (!requiresConfirmation(request.operation())) {
            throw new IllegalArgumentException("该用户操作不需要确认");
        }
        return withUser(userId, () -> {
            JsonNode args = request.arguments();
            long orderId = requiredLong(args, "order_id");
            String reason = requiredText(args, "reason");
            Object order = sanitize(orderService.detailsForUser(orderId));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("order_id", orderId);
            result.put("operation", request.operation());
            result.put("reason", reason);
            result.put("order", order);
            result.put("resource_version", hash(order));
            result.put("refund_expectation", "退款结果以业务服务实时校验为准");
            return result;
        });
    }

    /** 在确认后重新读取订单并执行，resourceVersion不一致时拒绝写入。 */
    public AgentToolOperationResponse executeConfirmed(AgentToolOperationRequest request, long userId,
                                                       String expectedVersion, String traceId) {
        try {
            Map<String, Object> preview = previewConfirmedOperation(request, userId);
            if (!String.valueOf(preview.get("resource_version")).equals(expectedVersion)) {
                return AgentToolOperationResponse.error(request.toolCallId(), "rejected",
                        "STALE_RESOURCE", "订单状态已变化，请重新发起操作", traceId);
            }
            Object data = withUser(userId, () -> {
                long orderId = requiredLong(request.arguments(), "order_id");
                AfterSaleApplyDTO dto = new AfterSaleApplyDTO();
                dto.setReason(requiredText(request.arguments(), "reason"));
                return afterSaleService.apply(userId, orderId, dto,
                        request.taskId() + ":" + request.toolCallId());
            });
            return AgentToolOperationResponse.success(request.toolCallId(), sanitize(data), traceId);
        } catch (IllegalArgumentException exception) {
            return AgentToolOperationResponse.error(request.toolCallId(), "rejected",
                    "INVALID_ARGUMENT", exception.getMessage(), traceId);
        }
    }

    private Object previewOrderAction(JsonNode args) {
        long orderId = requiredLong(args, "order_id");
        String action = requiredText(args, "action");
        if (!Set.of("CANCELLATION", "AFTER_SALE").contains(action)) {
            throw new IllegalArgumentException("action不受支持");
        }
        Object order = sanitize(orderService.detailsForUser(orderId));
        return Map.of("order_id", orderId, "action", action, "order", order,
                "resource_version", hash(order));
    }

    private Object remindOrder(AgentToolOperationRequest request, JsonNode args, long userId) {
        long orderId = requiredLong(args, "order_id");
        String key = "sky:user-agent:reminder:" + userId + ":" + orderId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                key, request.toolCallId(), Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(acquired)) {
            return Map.of("accepted", true, "replayed", true, "cooldown_seconds", 300);
        }
        try {
            orderService.reminder(orderId);
            return Map.of("accepted", true, "replayed", false, "cooldown_seconds", 300);
        } catch (RuntimeException exception) {
            redisTemplate.delete(key);
            throw exception;
        }
    }

    private <T> T withUser(long userId, java.util.function.Supplier<T> action) {
        Long previousId = BaseContext.getCurrentId();
        String previousRole = BaseContext.getCurrentRole();
        try {
            BaseContext.setCurrentId(userId);
            BaseContext.setCurrentRole(null);
            return action.get();
        } finally {
            BaseContext.removeCurrentId();
            if (previousId != null) BaseContext.setCurrentId(previousId);
            if (previousRole != null) BaseContext.setCurrentRole(previousRole);
        }
    }

    private String hash(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成订单资源版本", exception);
        }
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

    private List<String> stringList(JsonNode args, String name) {
        JsonNode value = args == null ? null : args.get(name);
        if (value == null || value.isNull()) return List.of();
        if (!value.isArray()) throw new IllegalArgumentException(name + "必须是数组");
        List<String> result = new java.util.ArrayList<>();
        value.forEach(item -> {
            String text = item.asText().trim();
            if (!text.isEmpty()) result.add(text);
        });
        return result;
    }
}
