package com.sky.service.diet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.DietRecommendationDTO;
import com.sky.dto.DishNutritionDTO;
import com.sky.dto.UserDietProfileDTO;
import com.sky.exception.AgentBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.diet.DietMapper;
import com.sky.vo.DietRecommendationVO;
import com.sky.vo.UserDietProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 使用确定性规则完成饮食推荐。LLM 只能解释本服务返回的事实，不能绕过安全过滤。
 */
@Service
@RequiredArgsConstructor
public class DietRecommendationServiceImpl implements DietRecommendationService {
    private static final Set<String> ALLERGEN_STATUSES = Set.of(
            "FREE", "CONTAINS", "MAY_CONTAIN", "CROSS_CONTACT_RISK", "UNKNOWN");
    private static final Set<String> CONSTRAINT_TYPES = Set.of(
            "ALLERGEN", "EXCLUDED_INGREDIENT", "DOCTOR_RESTRICTION", "DISLIKE");
    private static final Set<String> GOALS = Set.of(
            "WEIGHT_LOSS", "HIGH_PROTEIN", "LOW_SODIUM", "LOW_SUGAR", "LOW_FAT", "LIGHT");
    private static final Set<String> SUPPORTED_CONDITIONS = Set.of(
            "HYPERTENSION", "DIABETES", "HYPERLIPIDEMIA", "OBESITY");

    private final DietMapper mapper;
    private final DishMapper dishMapper;
    private final ObjectMapper objectMapper;
    private boolean recommendationEnabled = true;
    private boolean profileEnabled = true;
    private boolean medicalScenariosEnabled = true;
    private boolean allowSimulatedData;
    private MeterRegistry meterRegistry;

    @Value("${sky.diet.recommendation-enabled:true}")
    public void setRecommendationEnabled(boolean value) { this.recommendationEnabled = value; }
    @Value("${sky.diet.profile-enabled:true}")
    public void setProfileEnabled(boolean value) { this.profileEnabled = value; }
    @Value("${sky.diet.medical-scenarios-enabled:true}")
    public void setMedicalScenariosEnabled(boolean value) { this.medicalScenariosEnabled = value; }
    @Value("${sky.diet.allow-simulated-data:false}")
    public void setAllowSimulatedData(boolean value) { this.allowSimulatedData = value; }
    @Autowired(required = false)
    public void setMeterRegistry(MeterRegistry value) { this.meterRegistry = value; }

    @Override
    public UserDietProfileVO getProfile(long userId) {
        Map<String, Object> profile = mapper.getProfile(userId);
        if (profile == null) return null;
        return UserDietProfileVO.builder()
                .regionCode(text(profile.get("regionCode")))
                .dietaryPattern(text(profile.get("dietaryPattern")))
                .consentVersion(text(profile.get("consentVersion")))
                .consentedAt((LocalDateTime) profile.get("consentedAt"))
                .constraints(mapper.listConstraints(userId))
                .goals(mapper.listGoals(userId)).build();
    }

    @Override
    @Transactional
    public UserDietProfileVO saveProfile(long userId, UserDietProfileDTO request, String traceId) {
        if (!profileEnabled) throw new AgentBusinessException("饮食档案功能当前未开放");
        validateProfile(request);
        mapper.upsertProfile(userId, normalizeNullable(request.getRegionCode()),
                normalizeNullable(request.getDietaryPattern()), request.getConsentVersion().trim());
        mapper.deleteConstraints(userId);
        for (UserDietProfileDTO.ConstraintItem item : request.getConstraints()) {
            String type = normalize(item.getType());
            mapper.insertConstraint(userId, type, constraintCode(type, item.getCode()),
                    normalizeDefault(item.getSeverity(), "STRICT"),
                    normalizeDefault(item.getSourceType(), "USER"));
        }
        mapper.deleteGoals(userId);
        for (UserDietProfileDTO.GoalItem item : request.getGoals()) {
            mapper.insertGoal(userId, normalize(item.getCode()), Math.max(0, Math.min(100, item.getPriority())));
        }
        mapper.insertProfileAudit(userId, "UPSERT", "{\"fields\":\"structured\"}", traceId);
        increment("diet_profile_consent_total", "action", "upsert");
        return getProfile(userId);
    }

    @Override
    @Transactional
    public void deleteProfile(long userId, String traceId) {
        mapper.deleteConstraints(userId);
        mapper.deleteGoals(userId);
        mapper.deleteProfile(userId);
        mapper.insertProfileAudit(userId, "DELETE", "{\"deleted\":true}", traceId);
        increment("diet_profile_consent_total", "action", "delete");
    }

    @Override
    public Map<String, Object> getNutrition(long dishId) {
        if (dishMapper.getById(dishId) == null) throw new AgentBusinessException("菜品不存在");
        Map<String, Object> result = mapper.getLatestNutrition(dishId);
        return result == null ? Map.of() : result;
    }

    @Override
    @Transactional
    public void saveNutrition(long dishId, DishNutritionDTO request, long operator) {
        if (dishMapper.getById(dishId) == null) throw new AgentBusinessException("菜品不存在");
        validateNutrition(request);
        mapper.ensureRecipe(dishId, request.getRecipeVersion(), operator);
        Map<String, Object> value = objectMapper.convertValue(request, new TypeReference<>() { });
        value.put("dishId", dishId);
        value.put("dataCompleteness", completeness(request));
        mapper.upsertNutrition(value);
        mapper.deleteDishIngredients(dishId, request.getRecipeVersion());
        for (DishNutritionDTO.IngredientItem ingredient : request.getIngredients()) {
            String code = normalize(ingredient.getCode());
            mapper.upsertIngredient(code, ingredient.getName().trim());
            mapper.insertDishIngredient(dishId, request.getRecipeVersion(), mapper.getIngredientId(code),
                    ingredient.getAmountG(), normalizeDefault(ingredient.getRoleType(), "PRIMARY"),
                    ingredient.isReplaceable());
        }
        mapper.deleteDishAllergens(dishId, request.getRecipeVersion());
        for (DishNutritionDTO.AllergenItem allergen : request.getAllergens()) {
            mapper.insertDishAllergen(dishId, request.getRecipeVersion(), normalize(allergen.getCode()),
                    normalize(allergen.getStatus()), allergen.getSourceReference().trim());
        }
    }

    @Override
    @Transactional
    public void verifyNutrition(long dishId, int profileVersion, long operator) {
        Map<String, Object> nutrition = mapper.getLatestNutrition(dishId);
        if (nutrition == null || number(nutrition.get("profile_version")).intValue() != profileVersion) {
            throw new AgentBusinessException("营养档案版本不存在");
        }
        if (decimal(nutrition.get("data_completeness")).compareTo(new BigDecimal("70")) < 0) {
            throw new AgentBusinessException("营养数据完整度不足70%，不能审核启用");
        }
        int recipeVersion = number(nutrition.get("recipe_version")).intValue();
        if (mapper.verifyNutrition(dishId, profileVersion, operator) != 1) {
            throw new AgentBusinessException("只有草稿营养版本可以审核");
        }
        mapper.verifyRecipe(dishId, recipeVersion, operator);
    }

    @Override
    @Transactional
    public Map<String, Object> recalculateSetmealNutrition(long setmealId) {
        if (mapper.countSetmeal(setmealId) == 0) throw new AgentBusinessException("套餐不存在");
        Map<String, Object> aggregate = mapper.aggregateSetmealNutrition(setmealId, allowSimulatedData);
        if (aggregate == null || number(aggregate.get("componentCount")).intValue() == 0
                || number(aggregate.get("componentCount")).intValue() != number(aggregate.get("verifiedCount")).intValue()) {
            throw new AgentBusinessException("套餐全部组成菜品完成营养审核后才能生成快照");
        }
        List<Map<String, Object>> sources = mapper.listSetmealSources(setmealId, allowSimulatedData);
        Map<String, String> allergens = new LinkedHashMap<>();
        for (Map<String, Object> source : sources) {
            String code = text(source.get("allergenCode"));
            if (code == null) continue;
            String status = normalize(text(source.get("declarationStatus")));
            allergens.merge(normalize(code), status, this::moreRestrictiveAllergen);
        }
        Map<String, Object> snapshot = new HashMap<>(aggregate);
        snapshot.put("setmealId", setmealId);
        snapshot.put("snapshotVersion", mapper.getMaxSetmealSnapshotVersion(setmealId) + 1);
        snapshot.put("compositionHash", sha256(json(sources)));
        snapshot.put("allergensJson", json(allergens));
        snapshot.put("sourceVersionsJson", json(sources));
        snapshot.put("verificationStatus", allowSimulatedData ? "SIMULATED" : "VERIFIED");
        mapper.insertSetmealSnapshot(snapshot);
        return snapshot;
    }

    @Override
    @Transactional
    public DietRecommendationVO recommend(long userId, String idempotencyKey,
                                          DietRecommendationDTO request, String traceId) {
        if (!recommendationEnabled) throw new AgentBusinessException("个性化饮食推荐当前未开放");
        increment("diet_recommendation_requests_total", "scene", normalize(request.getScene()));
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new AgentBusinessException("Idempotency-Key格式不正确");
        }
        validateRecommendation(request);
        Map<String, Object> existing = mapper.getRecommendationByKey(userId, idempotencyKey);
        if (existing != null) return replay(existing);

        Set<String> allergens = normalizedSet(request.getAllergens());
        Set<String> excludedIngredients = normalizedIngredientSet(request.getExcludedIngredients());
        Set<String> goals = normalizedSet(request.getGoals());
        if (request.isUseSavedProfile()) mergeProfile(userId, allergens, excludedIngredients, goals);
        Set<String> conditions = normalizedSet(request.getConditions());
        Set<String> hardConstraints = normalizedSet(request.getHardConstraints());
        if ("COMMON_COLD".equals(normalize(request.getScene()))) {
            hardConstraints.addAll(Set.of("NO_ALCOHOL", "NO_SPICY", "NO_HIGH_OIL", "NO_HIGH_SODIUM_PICKLED"));
        }
        if (!conditions.isEmpty() && !medicalScenariosEnabled) {
            return persistEmpty(userId, idempotencyKey, request, traceId, "L3", "FEATURE_DISABLED",
                    List.of("健康情况推荐当前未开放，可改用普通口味和预算推荐"));
        }
        String riskLevel = "COMMON_COLD".equals(normalize(request.getScene()))
                || "SEASONAL_REGIONAL".equals(normalize(request.getScene()))
                ? "L1" : riskLevel(allergens, goals, conditions);
        List<String> unsupported = conditions.stream()
                .filter(value -> !SUPPORTED_CONDITIONS.contains(value)).toList();
        if (!unsupported.isEmpty()) {
            increment("diet_high_risk_degraded_total", "reason", "unsupported_condition");
            return persistEmpty(userId, idempotencyKey, request, traceId, "L4",
                    "REQUIRES_PROFESSIONAL", List.of("该情况需要医生或临床营养师结合病情评估，系统不生成个体化医疗饮食方案"));
        }
        mapConditionsToGoals(conditions, goals);

        List<Map<String, Object>> activeRules = mapper.listActiveRules();
        List<Map<String, Object>> applicableRules = activeRules.stream()
                .filter(rule -> conditions.contains(normalize(text(rule.get("ruleCode"))))
                        || goals.contains(normalize(text(rule.get("ruleCode")))))
                .toList();
        if (!conditions.isEmpty() && conditions.stream().noneMatch(condition -> activeRules.stream()
                .anyMatch(rule -> condition.equals(normalize(text(rule.get("ruleCode"))))))) {
            return persistEmpty(userId, idempotencyKey, request, traceId, riskLevel,
                    "RULES_UNAVAILABLE", List.of("对应健康情况尚无已审核发布的食养规则，系统没有生成疾病个性化推荐"));
        }

        List<Map<String, Object>> candidates = mapper.listRecommendationCandidates(
                request.getBudget(), allowSimulatedData);
        List<Long> dishIds = candidates.stream().filter(item -> "dish".equals(text(item.get("productType"))))
                .map(item -> number(item.get("productId")).longValue()).toList();
        Map<Long, Map<String, String>> declarations = allergenDeclarations(dishIds);
        Map<Long, Set<String>> ingredients = candidateIngredients(dishIds);
        Map<Long, Map<String, Object>> adaptations = candidateAdaptations(dishIds);
        Set<Long> seasonal = seasonalMatches(dishIds, request.getRegionCode());
        List<Map<String, Object>> activeRuleSets = mapper.listActiveRuleSets().stream()
                .filter(rule -> applicableRules.stream().anyMatch(item ->
                        text(item.get("ruleSetId")).equals(text(rule.get("ruleSetId")))))
                .toList();
        String recommendationId = UUID.randomUUID().toString().replace("-", "");
        List<ScoredCandidate> scored = new ArrayList<>();
        List<DietRecommendationVO.ExcludedItem> excluded = new ArrayList<>();

        for (Map<String, Object> candidate : candidates) {
            long dishId = number(candidate.get("productId")).longValue();
            boolean setmeal = "setmeal".equals(text(candidate.get("productType")));
            Evaluation evaluation = evaluate(candidate, allergens, excludedIngredients,
                    goals, setmeal ? Map.of() : declarations.getOrDefault(dishId, Map.of()),
                    setmeal ? Set.of() : ingredients.getOrDefault(dishId, Set.of()),
                    !setmeal && seasonal.contains(dishId), applicableRules, setmeal,
                    request.getScene(), adaptations.getOrDefault(dishId, Map.of()), hardConstraints);
            persistCandidate(recommendationId, candidate, evaluation);
            if (!evaluation.eligible()) {
                excluded.add(DietRecommendationVO.ExcludedItem.builder()
                        .productType(text(candidate.get("productType")))
                        .productId(number(candidate.get("productId")).longValue())
                        .name(text(candidate.get("name"))).reasonCodes(evaluation.reasons()).build());
                increment("diet_candidates_excluded_total", "reason",
                        evaluation.reasons().isEmpty() ? "unknown" : evaluation.reasons().getFirst().split(":")[0]);
            }
            if (evaluation.eligible()) scored.add(new ScoredCandidate(candidate, evaluation));
        }
        scored.sort(Comparator.comparing((ScoredCandidate value) -> value.evaluation().score()).reversed()
                .thenComparing(value -> number(value.candidate().get("productId")).longValue()));
        validateResultConsistency(scored, excluded);

        Map<String, Object> session = sessionValue(userId, idempotencyKey, request, traceId,
                recommendationId, riskLevel, scored.isEmpty() ? "NO_MATCH" : "COMPLETED", activeRuleSets);
        mapper.insertRecommendationSession(session);
        List<DietRecommendationVO.Item> items = scored.stream().limit(request.getLimit())
                .map(value -> item(value.candidate(), value.evaluation(), activeRuleSets)).toList();
        List<String> notices = new ArrayList<>();
        if (items.isEmpty()) notices.add("当前没有同时满足安全约束和数据完整度要求的在售菜品，系统没有放宽过敏或医生限制");
        if (items.isEmpty()) increment("diet_no_result_total", "risk_level", riskLevel);
        if (!conditions.isEmpty()) notices.add("结果仅用于辅助点餐，不能替代医生或临床营养师的个体化方案");
        if (allowSimulatedData) notices.add("当前使用开发模拟营养数据，不能用于真实医疗或严重过敏决策");
        return DietRecommendationVO.builder().recommendationId(recommendationId)
                .scene(normalize(request.getScene())).riskLevel(riskLevel)
                .status(items.isEmpty() ? "NO_MATCH" : "COMPLETED")
                .notices(notices).items(items).excludedItems(excluded).build();
    }

    @Override
    public void feedback(long userId, String recommendationId, Map<String, Object> request) {
        String type = normalize(String.valueOf(request.getOrDefault("product_type", "dish")));
        long productId = Long.parseLong(String.valueOf(request.get("product_id")));
        String feedbackType = normalize(String.valueOf(request.get("feedback_type")));
        if (!Set.of("ACCEPTED", "ADDED_TO_CART", "REPLACED", "DISLIKED").contains(feedbackType)) {
            throw new AgentBusinessException("反馈类型不受支持");
        }
        mapper.insertFeedback(Map.of("recommendationId", recommendationId, "userId", userId,
                "productType", type.toLowerCase(Locale.ROOT), "productId", productId,
                "feedbackType", feedbackType, "reasonCode",
                String.valueOf(request.getOrDefault("reason_code", ""))));
    }

    @Override
    public Map<String, Object> qualityDashboard() { return mapper.qualityDashboard(); }

    private Evaluation evaluate(Map<String, Object> candidate, Set<String> allergens,
                                Set<String> excludedIngredients, Set<String> goals,
                                Map<String, String> declarations, Set<String> ingredients,
                                boolean seasonal, List<Map<String, Object>> rules, boolean setmeal,
                                String scene, Map<String, Object> adaptation, Set<String> hardConstraints) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String allergen : allergens) {
            String status = declarations.get(allergen);
            if (status == null || !"FREE".equals(status)) {
                reasons.add(status == null || "UNKNOWN".equals(status)
                        ? "ALLERGEN_UNKNOWN:" + allergen : "ALLERGEN_" + status + ":" + allergen);
                return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
            }
        }
        Set<String> ingredientConflict = new HashSet<>(ingredients);
        ingredientConflict.retainAll(excludedIngredients);
        if (!ingredientConflict.isEmpty()) {
            reasons.add("EXCLUDED_INGREDIENT:" + ingredientConflict.iterator().next());
            return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
        }
        if (setmeal && !excludedIngredients.isEmpty()) {
            reasons.add("INGREDIENT_DATA_UNKNOWN");
            return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
        }
        if ("COMMON_COLD".equals(normalize(scene))) {
            if (setmeal || adaptation.isEmpty()) {
                reasons.add("ADAPTATION_DATA_UNKNOWN");
                return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
            }
            if (hardConstraints.contains("NO_SPICY") && numberOrNull(adaptation.get("spicyLevel"), 0) >= 2) {
                reasons.add("NO_SPICY");
                return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
            }
            if (hardConstraints.contains("NO_HIGH_OIL") && numberOrNull(adaptation.get("oilLevel"), 0) >= 3) {
                reasons.add("NO_HIGH_OIL");
                return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
            }
            if (hardConstraints.contains("NO_HIGH_SODIUM_PICKLED") && (numberOrNull(adaptation.get("saltLevel"), 0) >= 4
                    || "TRUE".equals(codeOrUnknown(adaptation.get("pickledFood"))))) {
                reasons.add("NO_HIGH_SODIUM_PICKLED");
                return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
            }
            if (hardConstraints.contains("NO_ALCOHOL") && Set.of("CONTAINS", "POSSIBLE").contains(codeOrUnknown(adaptation.get("alcoholContent")))) {
                reasons.add("NO_ALCOHOL");
                return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, Map.of());
            }
            if ("WARM".equals(codeOrUnknown(adaptation.get("temperatureType")))) reasons.add("WARM");
            if ("LIGHT".equals(codeOrUnknown(adaptation.get("soupBaseType")))) reasons.add("LIGHT");
            if ("EASY".equals(codeOrUnknown(adaptation.get("digestibility")))) reasons.add("EASY_TO_DIGEST");
        }
        BigDecimal score = new BigDecimal("50");
        Map<String, BigDecimal> details = new LinkedHashMap<>();
        if ("COMMON_COLD".equals(normalize(scene)) && !adaptation.isEmpty()) {
            if ("WARM".equals(codeOrUnknown(adaptation.get("temperatureType")))) {
                score = score.add(new BigDecimal("8")); details.put("warm", new BigDecimal("8"));
            }
            if ("LIGHT".equals(codeOrUnknown(adaptation.get("soupBaseType")))) {
                score = score.add(new BigDecimal("8")); details.put("light", new BigDecimal("8"));
            }
            if ("EASY".equals(codeOrUnknown(adaptation.get("digestibility")))) {
                score = score.add(new BigDecimal("8")); details.put("digestibility", new BigDecimal("8"));
            }
            BigDecimal oilPreference = BigDecimal.valueOf(Math.max(0,
                    5 - numberOrNull(adaptation.get("oilLevel"), 5))).multiply(new BigDecimal("2"));
            BigDecimal saltPreference = BigDecimal.valueOf(Math.max(0,
                    5 - numberOrNull(adaptation.get("saltLevel"), 5)));
            score = score.add(oilPreference).add(saltPreference);
            details.put("lowerOil", oilPreference); details.put("lowerSalt", saltPreference);
        }
        if (seasonal) { score = score.add(new BigDecimal("8")); details.put("seasonal", new BigDecimal("8")); reasons.add("SEASONAL_INGREDIENT"); }
        BigDecimal energy = decimal(candidate.get("energyKcal"));
        BigDecimal protein = decimal(candidate.get("proteinG"));
        BigDecimal fat = decimal(candidate.get("fatG"));
        BigDecimal sugar = decimal(candidate.get("sugarG"));
        BigDecimal sodium = decimal(candidate.get("sodiumMg"));
        if (goals.contains("WEIGHT_LOSS") && energy != null) score = addInverse(score, details, "energy", energy, new BigDecimal("700"), new BigDecimal("15"), reasons, "ENERGY_FRIENDLY");
        if (goals.contains("HIGH_PROTEIN") && protein != null) score = addPositive(score, details, "protein", protein, new BigDecimal("30"), new BigDecimal("15"), reasons, "HIGH_PROTEIN");
        if ((goals.contains("LOW_FAT") || goals.contains("LIGHT")) && fat != null) score = addInverse(score, details, "fat", fat, new BigDecimal("30"), new BigDecimal("10"), reasons, "LOWER_FAT");
        if (goals.contains("LOW_SUGAR") && sugar != null) score = addInverse(score, details, "sugar", sugar, new BigDecimal("30"), new BigDecimal("12"), reasons, "LOWER_SUGAR");
        if (goals.contains("LOW_SODIUM") && sodium != null) score = addInverse(score, details, "sodium", sodium, new BigDecimal("1200"), new BigDecimal("15"), reasons, "LOWER_SODIUM");
        if (goals.contains("LOW_SODIUM") && sodium == null) warnings.add("SODIUM_DATA_MISSING");
        if (goals.contains("LOW_SUGAR") && sugar == null) warnings.add("SUGAR_DATA_MISSING");
        Map<String, BigDecimal> nutrition = Map.ofEntries(
                Map.entry("ENERGY_KCAL", zeroIfNull(energy)), Map.entry("PROTEIN_G", zeroIfNull(protein)),
                Map.entry("FAT_G", zeroIfNull(fat)), Map.entry("SUGAR_G", zeroIfNull(sugar)),
                Map.entry("SODIUM_MG", zeroIfNull(sodium)));
        for (Map<String, Object> rule : rules) {
            String field = normalize(text(rule.get("targetField")));
            BigDecimal actual = nutrition.get(field);
            boolean missing = switch (field) {
                case "ENERGY_KCAL" -> energy == null; case "PROTEIN_G" -> protein == null;
                case "FAT_G" -> fat == null; case "SUGAR_G" -> sugar == null;
                case "SODIUM_MG" -> sodium == null; default -> true;
            };
            if (!matchesRule(actual, missing, rule)) continue;
            String action = normalize(text(rule.get("action")));
            String reason = normalize(text(rule.get("reasonCode")));
            if ("EXCLUDE".equals(action) || "REQUIRE_CLARIFICATION".equals(action)) {
                reasons.add(reason);
                return new Evaluation(false, BigDecimal.ZERO, reasons, warnings, details);
            }
            if ("WARN".equals(action)) warnings.add(reason);
            BigDecimal delta = decimal(rule.get("scoreDelta"));
            if (delta != null && ("BOOST".equals(action) || "PENALIZE".equals(action))) {
                BigDecimal signed = "PENALIZE".equals(action) ? delta.abs().negate() : delta.abs();
                score = score.add(signed); details.put("rule:" + reason, signed); reasons.add(reason);
            }
        }
        return new Evaluation(true, score.setScale(2, RoundingMode.HALF_UP), reasons, warnings, details);
    }

    private boolean matchesRule(BigDecimal actual, boolean missing, Map<String, Object> rule) {
        String operator = normalize(text(rule.get("operator")));
        if ("UNKNOWN".equals(operator)) return missing;
        if (missing) return false;
        BigDecimal expected;
        try { expected = new BigDecimal(text(rule.get("comparisonValue"))); }
        catch (Exception exception) { return false; }
        int compare = actual.compareTo(expected);
        return switch (operator) {
            case "EQ" -> compare == 0; case "LT" -> compare < 0; case "LTE" -> compare <= 0;
            case "GT" -> compare > 0; case "GTE" -> compare >= 0; default -> false;
        };
    }

    private BigDecimal zeroIfNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private BigDecimal addInverse(BigDecimal score, Map<String, BigDecimal> details, String key,
                                  BigDecimal value, BigDecimal ceiling, BigDecimal max,
                                  List<String> reasons, String reason) {
        BigDecimal delta = ceiling.subtract(value).max(BigDecimal.ZERO)
                .divide(ceiling, 4, RoundingMode.HALF_UP).multiply(max);
        details.put(key, delta); if (delta.signum() > 0) reasons.add(reason);
        return score.add(delta);
    }

    /** 返回前验证推荐与排除集合互斥且不重复，冲突时整次事务回滚。 */
    private void validateResultConsistency(List<ScoredCandidate> scored,
                                           List<DietRecommendationVO.ExcludedItem> excluded) {
        Set<String> recommended = new HashSet<>();
        for (ScoredCandidate value : scored) {
            String key = text(value.candidate().get("productType")) + ":"
                    + number(value.candidate().get("productId")).longValue();
            if (!recommended.add(key)) throw new AgentBusinessException("RESULT_CONFLICT: 推荐商品重复");
        }
        for (DietRecommendationVO.ExcludedItem value : excluded) {
            String key = value.getProductType() + ":" + value.getProductId();
            if (recommended.contains(key)) throw new AgentBusinessException("RESULT_CONFLICT: 排除商品进入推荐列表");
        }
    }

    private BigDecimal addPositive(BigDecimal score, Map<String, BigDecimal> details, String key,
                                   BigDecimal value, BigDecimal target, BigDecimal max,
                                   List<String> reasons, String reason) {
        BigDecimal delta = value.divide(target, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE).multiply(max);
        details.put(key, delta); if (delta.signum() > 0) reasons.add(reason);
        return score.add(delta);
    }

    private void persistCandidate(String recommendationId, Map<String, Object> candidate,
                                  Evaluation evaluation) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("recommendationId", recommendationId);
        trace.put("productType", candidate.get("productType"));
        trace.put("productId", candidate.get("productId"));
        trace.put("eligible", evaluation.eligible());
        trace.put("score", evaluation.score());
        trace.put("reasonCodesJson", json(evaluation.reasons()));
        trace.put("scoreDetailJson", json(evaluation.scoreDetails()));
        trace.put("dataVersionsJson", json(Map.of("nutrition", candidate.get("profileVersion"),
                "recipe", candidate.get("recipeVersion"))));
        mapper.insertCandidateTrace(trace);
    }

    private DietRecommendationVO.Item item(Map<String, Object> candidate, Evaluation evaluation,
                                            List<Map<String, Object>> rules) {
        Map<String, BigDecimal> nutrition = new LinkedHashMap<>();
        put(nutrition, "energy_kcal", candidate.get("energyKcal"));
        put(nutrition, "protein_g", candidate.get("proteinG"));
        put(nutrition, "fat_g", candidate.get("fatG"));
        put(nutrition, "carbohydrate_g", candidate.get("carbohydrateG"));
        put(nutrition, "dietary_fiber_g", candidate.get("dietaryFiberG"));
        put(nutrition, "sugar_g", candidate.get("sugarG"));
        put(nutrition, "sodium_mg", candidate.get("sodiumMg"));
        return DietRecommendationVO.Item.builder().productType(text(candidate.get("productType")))
                .productId(number(candidate.get("productId")).longValue()).name(text(candidate.get("name")))
                .price(decimal(candidate.get("price"))).image(text(candidate.get("image")))
                .score(evaluation.score()).nutrition(nutrition).matchReasons(evaluation.reasons())
                .warnings(evaluation.warnings()).ruleSources(rules.stream()
                        .map(value -> text(value.get("ruleSetId"))).filter(value -> value != null).toList()).build();
    }

    private DietRecommendationVO replay(Map<String, Object> existing) {
        String id = text(existing.get("recommendationId"));
        List<Map<String, Object>> traces = mapper.listCandidateTrace(id);
        return DietRecommendationVO.builder().recommendationId(id)
                .riskLevel(text(existing.get("riskLevel"))).status(text(existing.get("status")))
                .notices(List.of("该结果由相同幂等请求复用"))
                .items(traces.stream().filter(value -> Boolean.TRUE.equals(value.get("eligible"))
                                || number(value.get("eligible")).intValue() == 1)
                        .map(value -> DietRecommendationVO.Item.builder()
                                .productType(text(value.get("productType")))
                                .productId(number(value.get("productId")).longValue())
                                .score(decimal(value.get("score"))).matchReasons(jsonList(value.get("reasonCodesJson")))
                                .warnings(List.of()).nutrition(Map.of()).ruleSources(List.of()).build()).toList()).build();
    }

    private DietRecommendationVO persistEmpty(long userId, String key, DietRecommendationDTO request,
                                               String traceId, String risk, String status,
                                               List<String> notices) {
        String id = UUID.randomUUID().toString().replace("-", "");
        mapper.insertRecommendationSession(sessionValue(userId, key, request, traceId, id, risk, status, List.of()));
        return DietRecommendationVO.builder().recommendationId(id).riskLevel(risk).status(status)
                .notices(notices).items(List.of()).excludedItems(List.of()).build();
    }

    private Map<String, Object> sessionValue(long userId, String key, DietRecommendationDTO request,
                                             String traceId, String id, String risk, String status,
                                             List<Map<String, Object>> rules) {
        String requestJson = json(request);
        Map<String, Object> result = new HashMap<>();
        result.put("recommendationId", id); result.put("userId", userId); result.put("idempotencyKey", key);
        result.put("scene", normalize(request.getScene())); result.put("riskLevel", risk);
        result.put("requestHash", sha256(requestJson)); result.put("requestJson", requestJson);
        result.put("ruleVersionsJson", json(rules)); result.put("status", status); result.put("traceId", traceId);
        return result;
    }

    private void mergeProfile(long userId, Set<String> allergens, Set<String> excluded, Set<String> goals) {
        for (Map<String, Object> item : mapper.listConstraints(userId)) {
            String type = normalize(text(item.get("type")));
            if ("ALLERGEN".equals(type)) allergens.add(normalize(text(item.get("code"))));
            if ("EXCLUDED_INGREDIENT".equals(type) || "DOCTOR_RESTRICTION".equals(type)) {
                excluded.add(normalizeIngredient(text(item.get("code"))));
            }
        }
        mapper.listGoals(userId).forEach(item -> goals.add(normalize(text(item.get("code")))));
    }

    private Map<Long, Map<String, String>> allergenDeclarations(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Map<String, String>> result = new HashMap<>();
        mapper.listCandidateAllergens(ids).forEach(item -> result
                .computeIfAbsent(number(item.get("dishId")).longValue(), ignored -> new HashMap<>())
                .put(normalize(text(item.get("allergenCode"))), normalize(text(item.get("declarationStatus")))));
        return result;
    }

    private Map<Long, Set<String>> candidateIngredients(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Set<String>> result = new HashMap<>();
        mapper.listCandidateIngredients(ids).forEach(item -> {
            Set<String> values = result.computeIfAbsent(number(item.get("dishId")).longValue(), ignored -> new HashSet<>());
            values.add(normalize(text(item.get("ingredientCode"))));
            values.add(normalizeIngredient(text(item.get("ingredientName"))));
        });
        return result;
    }

    private Map<Long, Map<String, Object>> candidateAdaptations(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Map<String, Object>> result = new HashMap<>();
        mapper.listCandidateAdaptations(ids).forEach(item ->
                result.putIfAbsent(number(item.get("dishId")).longValue(), item));
        return result;
    }

    private Set<Long> seasonalMatches(List<Long> ids, String region) {
        if (ids.isEmpty()) return Set.of();
        return mapper.listSeasonalMatches(ids, normalizeDefault(region, "CN"), LocalDateTime.now().getMonthValue())
                .stream().map(item -> number(item.get("dishId")).longValue()).collect(Collectors.toSet());
    }

    private void mapConditionsToGoals(Set<String> conditions, Set<String> goals) {
        if (conditions.contains("HYPERTENSION")) goals.add("LOW_SODIUM");
        if (conditions.contains("DIABETES")) goals.add("LOW_SUGAR");
        if (conditions.contains("HYPERLIPIDEMIA")) goals.add("LOW_FAT");
        if (conditions.contains("OBESITY")) goals.add("WEIGHT_LOSS");
    }

    private String riskLevel(Set<String> allergens, Set<String> goals, Set<String> conditions) {
        if (!conditions.isEmpty()) return "L3";
        if (!allergens.isEmpty()) return "L2";
        return goals.isEmpty() ? "L0" : "L1";
    }

    private void validateProfile(UserDietProfileDTO request) {
        Set<String> unique = new HashSet<>();
        for (UserDietProfileDTO.ConstraintItem item : request.getConstraints()) {
            String type = normalize(item.getType());
            if (!CONSTRAINT_TYPES.contains(type)) throw new AgentBusinessException("饮食约束类型不受支持");
            if (!unique.add(type + ":" + constraintCode(type, item.getCode()))) throw new AgentBusinessException("饮食约束重复");
        }
        for (UserDietProfileDTO.GoalItem item : request.getGoals()) {
            if (!GOALS.contains(normalize(item.getCode()))) throw new AgentBusinessException("健康目标不受支持");
        }
    }

    private void validateNutrition(DishNutritionDTO request) {
        Set<String> allergens = new HashSet<>();
        for (DishNutritionDTO.AllergenItem item : request.getAllergens()) {
            if (!ALLERGEN_STATUSES.contains(normalize(item.getStatus()))) throw new AgentBusinessException("过敏原声明状态不受支持");
            if (!allergens.add(normalize(item.getCode()))) throw new AgentBusinessException("过敏原声明重复");
        }
    }

    private void validateRecommendation(DietRecommendationDTO request) {
        for (String goal : request.getGoals()) if (!GOALS.contains(normalize(goal))) throw new AgentBusinessException("健康目标不受支持: " + goal);
    }

    private BigDecimal completeness(DishNutritionDTO request) {
        Object[] fields = {request.getEnergyKcal(), request.getProteinG(), request.getFatG(),
                request.getCarbohydrateG(), request.getDietaryFiberG(), request.getSugarG(), request.getSodiumMg()};
        int present = 0; for (Object field : fields) if (field != null) present++;
        return BigDecimal.valueOf(present * 100L).divide(BigDecimal.valueOf(fields.length), 2, RoundingMode.HALF_UP);
    }

    private Set<String> normalizedSet(List<String> values) {
        if (values == null) return new LinkedHashSet<>();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizedIngredientSet(List<String> values) {
        if (values == null) return new LinkedHashSet<>();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(this::normalizeIngredient).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeIngredient(String value) {
        if (value == null || value.isBlank()) throw new AgentBusinessException("食材名称不能为空");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) throw new AgentBusinessException("食材名称过长");
        return normalized;
    }

    private String constraintCode(String type, String value) {
        return "ALLERGEN".equals(type) ? normalize(value) : normalizeIngredient(value);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new AgentBusinessException("编码不能为空");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_\\-]{1,64}")) throw new AgentBusinessException("编码格式不正确");
        return normalized;
    }

    private String normalizeDefault(String value, String fallback) { return normalize(value == null || value.isBlank() ? fallback : value); }
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : normalize(value); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Number number(Object value) { return value instanceof Number number ? number : new BigDecimal(String.valueOf(value)); }
    private BigDecimal decimal(Object value) { return value == null ? null : value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value)); }
    private int numberOrNull(Object value, int fallback) { return value == null ? fallback : number(value).intValue(); }
    private String codeOrUnknown(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "UNKNOWN" : normalize(String.valueOf(value));
    }
    private void put(Map<String, BigDecimal> result, String key, Object value) { BigDecimal parsed = decimal(value); if (parsed != null) result.put(key, parsed); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("无法序列化饮食推荐数据", exception); } }
    private List<String> jsonList(Object value) { try { return objectMapper.readValue(String.valueOf(value), new TypeReference<>() { }); } catch (Exception ignored) { return List.of(); } }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private String moreRestrictiveAllergen(String left, String right) {
        List<String> order = List.of("FREE", "UNKNOWN", "CROSS_CONTACT_RISK", "MAY_CONTAIN", "CONTAINS");
        return order.indexOf(left) >= order.indexOf(right) ? left : right;
    }
    private void increment(String name, String tag, String value) {
        if (meterRegistry != null) meterRegistry.counter(name, tag, value).increment();
    }

    private record Evaluation(boolean eligible, BigDecimal score, List<String> reasons,
                              List<String> warnings, Map<String, BigDecimal> scoreDetails) { }
    private record ScoredCandidate(Map<String, Object> candidate, Evaluation evaluation) { }
}
