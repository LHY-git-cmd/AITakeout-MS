package com.sky.service.agent;

import com.fasterxml.jackson.databind.*;
import com.sky.dto.AgentCreateDishesDTO;
import com.sky.dto.DishNutritionDTO;
import com.sky.entity.*;
import com.sky.mapper.*;
import com.sky.service.diet.DietRecommendationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 管理端菜品新增边界：校验、预览、原子批量保存和持久化幂等结果。 */
@Service
@RequiredArgsConstructor
public class AdminDishCreationService {
    private final CategoryMapper categoryMapper;
    private final DishMapper dishMapper;
    private final DishFlavorMapper flavorMapper;
    private final AgentToolConfirmationMapper confirmationMapper;
    private final DietRecommendationService nutritionService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /** 校验全部嵌套字段，拒绝模型额外传入起售状态或数据库ID。 */
    public AgentCreateDishesDTO validate(JsonNode args) {
        if (args == null || !args.isObject() || !args.path("test_data").isBoolean())
            throw new IllegalArgumentException("必须明确指定是否测试数据");
        for (JsonNode item : args.path("dishes")) {
            if (!item.path("category_id").isIntegralNumber() || !item.path("category_id").canConvertToLong())
                throw new IllegalArgumentException("分类ID必须是正整数");
        }
        final AgentCreateDishesDTO request;
        try {
            request = objectMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .treeToValue(args, AgentCreateDishesDTO.class);
        } catch (Exception ex) { throw new IllegalArgumentException("添加菜品参数格式无效", ex); }
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.stream()
                .map(e -> e.getPropertyPath() + ": " + e.getMessage()).sorted().findFirst().orElseThrow());
        if (request.getTestData() && request.getDishes().size() != 3)
            throw new IllegalArgumentException("测试数据必须恰好包含三道菜品");
        Set<String> names = new HashSet<>();
        for (var item : request.getDishes()) {
            item.setName(item.getName().trim());
            if (!names.add(item.getName().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("批次内菜品名称不能重复");
            requireCategory(categoryMapper.getById(item.getCategoryId()));
            if (item.getNutrition() != null) validateNutrition(item.getNutrition());
            if (request.getTestData()) {
                if (item.getDescription() == null || item.getDescription().isBlank())
                    throw new IllegalArgumentException("测试菜品必须提供描述");
                DishNutritionDTO n = item.getNutrition();
                if (n == null || !"SIMULATED".equals(n.getSourceType()) ||
                    n.getEnergyKcal() == null || n.getProteinG() == null || n.getFatG() == null ||
                    n.getCarbohydrateG() == null || n.getDietaryFiberG() == null ||
                    n.getSugarG() == null || n.getSodiumMg() == null ||
                    n.getIngredients().isEmpty() || n.getAllergens().isEmpty())
                    throw new IllegalArgumentException("测试菜品必须提供完整的模拟营养、食材和过敏原声明");
                n.setSourceReference("Agent生成的测试数据；非真实配方或安全保证");
                n.setUncertaintyNote("模拟估算，仅供测试，需管理员核实后审核");
            }
        }
        return request;
    }

    private void validateNutrition(DishNutritionDTO nutrition) {
        if (nutrition.getIngredients() == null || nutrition.getAllergens() == null ||
                nutrition.getIngredients().size() > 50 || nutrition.getAllergens().size() > 50)
            throw new IllegalArgumentException("食材和过敏原列表必须有效且最多50项");
        Set<String> codes = new HashSet<>();
        for (var ingredient : nutrition.getIngredients()) {
            if (ingredient == null || !Set.of("PRIMARY", "SECONDARY", "SEASONING").contains(ingredient.getRoleType()))
                throw new IllegalArgumentException("食材角色无效");
        }
        for (var allergen : nutrition.getAllergens()) {
            if (allergen == null) throw new IllegalArgumentException("过敏原声明不能为空");
            if (!Set.of("FREE", "CONTAINS", "MAY_CONTAIN", "CROSS_CONTACT_RISK", "UNKNOWN")
                    .contains(allergen.getStatus()) || !codes.add(allergen.getCode().trim().toUpperCase(Locale.ROOT)))
                throw new IllegalArgumentException("过敏原状态无效或声明重复");
        }
    }

    private Category requireCategory(Category category) {
        if (category == null || !Integer.valueOf(1).equals(category.getType()) ||
                !Integer.valueOf(1).equals(category.getStatus()))
            throw new IllegalArgumentException("请选择已启用的菜品分类；没有分类时请先创建分类");
        return category;
    }

    /** 只返回真实且启用的菜品分类，不自动建分类。 */
    public List<Category> categories() { return categoryMapper.list(1); }

    /** 确认预览加入真实分类名称，图片空缺及营养审核状态明确可见。 */
    public Map<String, Object> preview(JsonNode args) {
        var request = validate(args);
        List<Map<String, Object>> dishes = new ArrayList<>();
        for (var item : request.getDishes()) {
            Map<String, Object> row = objectMapper.convertValue(item, Map.class);
            row.put("categoryName", requireCategory(categoryMapper.getById(item.getCategoryId())).getName());
            row.put("status", 0);
            row.put("nutritionStatus", item.getNutrition() == null ? "未提供" : "待审核");
            dishes.add(row);
        }
        return Map.of("test_data", request.getTestData(), "dishes", dishes);
    }

    /** 分类变化会使确认失效，防止管理员确认的分类与实际保存不一致。 */
    public String categoryVersion(JsonNode args) {
        var request = validate(args);
        return request.getDishes().stream().map(i -> categoryMapper.getById(i.getCategoryId()))
                .sorted(Comparator.comparing(Category::getId))
                .map(c -> c.getId() + ":" + c.getName() + ":" + c.getStatus() + ":" + c.getUpdateTime())
                .reduce("", (a, b) -> a + "|" + b);
    }

    /** 重复确认只返回已存结果；菜品和营养写入、结果及状态一起提交或回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public JsonNode create(String confirmationId, JsonNode args, long operator, String confirmedVersion) throws Exception {
        String status = confirmationMapper.lockStatus(confirmationId);
        if ("EXECUTED".equals(status)) return result(confirmationId);
        if (!"EXECUTING".equals(status)) throw new IllegalArgumentException("菜品新增操作尚未确认");
        var request = validate(args);
        // 按ID顺序加锁，批次间使用相同锁顺序，减少并发死锁。
        request.getDishes().stream().map(AgentCreateDishesDTO.Item::getCategoryId).distinct().sorted()
                .forEach(id -> requireCategory(categoryMapper.lockById(id)));
        String lockedVersion = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(categoryVersion(args).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (!Objects.equals(confirmedVersion, lockedVersion))
            throw new IllegalArgumentException("分类已变化，请重新确认添加菜品");
        List<Map<String, Object>> created = new ArrayList<>();
        for (var item : request.getDishes()) {
            if (dishMapper.countByName(item.getName()) > 0)
                throw new IllegalArgumentException("菜品名称已存在：" + item.getName());
            Dish dish = new Dish();
            dish.setName(item.getName()); dish.setCategoryId(item.getCategoryId());
            dish.setPrice(item.getPrice()); dish.setImage(item.getImage() == null ? "" : item.getImage());
            dish.setDescription(item.getDescription()); dish.setStatus(0);
            dish.setCreateUser(operator); dish.setUpdateUser(operator);
            dish.setCreateTime(LocalDateTime.now()); dish.setUpdateTime(dish.getCreateTime());
            dishMapper.insert(dish);
            if (item.getFlavors() != null && !item.getFlavors().isEmpty()) {
                List<DishFlavor> flavors = item.getFlavors().stream().map(f -> {
                    DishFlavor value = new DishFlavor(); value.setDishId(dish.getId());
                    value.setName(f.getName()); value.setValue(f.getValue()); return value;
                }).toList();
                flavorMapper.insertBatch(flavors);
            }
            if (item.getNutrition() != null) nutritionService.saveNutrition(dish.getId(), item.getNutrition(), operator);
            created.add(Map.of("id", dish.getId(), "name", dish.getName(), "status", 0,
                    "image_pending", dish.getImage().isBlank()));
        }
        JsonNode result = objectMapper.valueToTree(Map.of("dishes", created, "test_data", request.getTestData(),
                "message", "菜品已添加并停售；请上传图片并核实营养与过敏原信息"));
        if (confirmationMapper.completeDishCreation(confirmationId, objectMapper.writeValueAsString(result)) != 1)
            throw new IllegalStateException("无法保存菜品新增结果");
        return result;
    }

    public JsonNode result(String confirmationId) {
        try {
            String json = confirmationMapper.getResult(confirmationId);
            if (json == null) throw new IllegalArgumentException("菜品新增结果不存在");
            return objectMapper.readTree(json);
        } catch (java.io.IOException ex) { throw new IllegalStateException("菜品新增结果无效", ex); }
    }
}
