package com.sky.service.diet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.DietRecommendationDTO;
import com.sky.mapper.DishMapper;
import com.sky.mapper.diet.DietMapper;
import com.sky.vo.DietRecommendationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/** 饮食推荐的硬约束和医疗边界测试。 */
@ExtendWith(MockitoExtension.class)
class DietRecommendationServiceTest {
    @Mock private DietMapper mapper;
    @Mock private DishMapper dishMapper;
    private DietRecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DietRecommendationServiceImpl(mapper, dishMapper, new ObjectMapper());
        lenient().when(mapper.getRecommendationByKey(anyLong(), anyString())).thenReturn(null);
        lenient().when(mapper.listActiveRules()).thenReturn(List.of());
    }

    @Test
    void unknownAllergenDeclarationMustNotBeTreatedAsSafe() {
        DietRecommendationDTO request = request();
        request.setAllergens(new ArrayList<>(List.of("PEANUT")));
        when(mapper.listRecommendationCandidates(any(), anyBoolean())).thenReturn(List.of(candidate(1L)));
        when(mapper.listCandidateAllergens(List.of(1L))).thenReturn(List.of());
        when(mapper.listCandidateIngredients(List.of(1L))).thenReturn(List.of());
        when(mapper.listSeasonalMatches(any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.listActiveRuleSets()).thenReturn(List.of());

        DietRecommendationVO result = service.recommend(7L, "diet-test-0001", request, "trace-1");

        assertThat(result.getStatus()).isEqualTo("NO_MATCH");
        assertThat(result.getItems()).isEmpty();
        verify(mapper).insertCandidateTrace(any());
    }

    @Test
    void verifiedFreeAllergenCanPassAndProduceExplainableResult() {
        DietRecommendationDTO request = request();
        request.setAllergens(new ArrayList<>(List.of("PEANUT")));
        request.setGoals(new ArrayList<>(List.of("HIGH_PROTEIN")));
        when(mapper.listRecommendationCandidates(any(), anyBoolean())).thenReturn(List.of(candidate(1L)));
        when(mapper.listCandidateAllergens(List.of(1L))).thenReturn(List.of(Map.of(
                "dishId", 1L, "allergenCode", "PEANUT", "declarationStatus", "FREE")));
        when(mapper.listCandidateIngredients(List.of(1L))).thenReturn(List.of());
        when(mapper.listSeasonalMatches(any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.listActiveRuleSets()).thenReturn(List.of());

        DietRecommendationVO result = service.recommend(7L, "diet-test-0002", request, "trace-2");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getMatchReasons()).contains("HIGH_PROTEIN");
        assertThat(result.getItems().getFirst().getNutrition()).containsKey("protein_g");
    }

    @Test
    void unsupportedMedicalConditionRequiresProfessionalInsteadOfRecommendation() {
        DietRecommendationDTO request = request();
        request.setConditions(new ArrayList<>(List.of("CHRONIC_KIDNEY_DISEASE")));

        DietRecommendationVO result = service.recommend(7L, "diet-test-0003", request, "trace-3");

        assertThat(result.getRiskLevel()).isEqualTo("L4");
        assertThat(result.getStatus()).isEqualTo("REQUIRES_PROFESSIONAL");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void publishedConditionRuleCanExcludeCandidateDeterministically() {
        DietRecommendationDTO request = request();
        request.setConditions(new ArrayList<>(List.of("HYPERTENSION")));
        when(mapper.listActiveRules()).thenReturn(List.of(Map.of(
                "ruleSetId", "rule-hbp-v1", "ruleCode", "HYPERTENSION",
                "targetField", "SODIUM_MG", "operator", "GT", "comparisonValue", "400",
                "action", "EXCLUDE", "reasonCode", "SODIUM_OVER_LIMIT", "message", "钠较高")));
        when(mapper.listActiveRuleSets()).thenReturn(List.of(Map.of(
                "ruleSetId", "rule-hbp-v1", "ruleCode", "HYPERTENSION", "ruleVersion", 1)));
        when(mapper.listRecommendationCandidates(any(), anyBoolean())).thenReturn(List.of(candidate(1L)));
        when(mapper.listCandidateAllergens(List.of(1L))).thenReturn(List.of());
        when(mapper.listCandidateIngredients(List.of(1L))).thenReturn(List.of());
        when(mapper.listSeasonalMatches(any(), any(), anyInt())).thenReturn(List.of());

        DietRecommendationVO result = service.recommend(7L, "diet-test-0004", request, "trace-4");

        assertThat(result.getStatus()).isEqualTo("NO_MATCH");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void setmealSnapshotRequiresEveryComponentToHaveVerifiedNutrition() {
        when(mapper.countSetmeal(33L)).thenReturn(1);
        when(mapper.aggregateSetmealNutrition(33L, false)).thenReturn(Map.of(
                "componentCount", 3, "verifiedCount", 2));

        assertThatThrownBy(() -> service.recalculateSetmealNutrition(33L))
                .hasMessageContaining("全部组成菜品完成营养审核");
    }

    @Test
    void chineseIngredientExclusionIsAcceptedAndApplied() {
        DietRecommendationDTO request = request();
        request.setExcludedIngredients(new ArrayList<>(List.of("大蒜")));
        when(mapper.listRecommendationCandidates(any(), anyBoolean())).thenReturn(List.of(candidate(1L)));
        when(mapper.listCandidateAllergens(List.of(1L))).thenReturn(List.of());
        when(mapper.listCandidateIngredients(List.of(1L))).thenReturn(List.of(Map.of(
                "dishId", 1L, "ingredientCode", "GARLIC", "ingredientName", "大蒜")));
        when(mapper.listSeasonalMatches(any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.listActiveRuleSets()).thenReturn(List.of());

        DietRecommendationVO result = service.recommend(7L, "diet-test-0005", request, "trace-5");

        assertThat(result.getStatus()).isEqualTo("NO_MATCH");
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void commonColdExcludesSpicyOilyPickledAndAlcoholCandidates() {
        DietRecommendationDTO request = request();
        request.setScene("COMMON_COLD");
        request.setHardConstraints(new ArrayList<>(List.of(
                "NO_ALCOHOL", "NO_SPICY", "NO_HIGH_OIL", "NO_HIGH_SODIUM_PICKLED")));
        Map<String, Object> soup = candidate(68L); soup.put("name", "鸡蛋汤");
        Map<String, Object> fish = candidate(51L); fish.put("name", "老坛酸菜鱼");
        Map<String, Object> beer = candidate(48L); beer.put("name", "雪花啤酒");
        when(mapper.listRecommendationCandidates(any(), anyBoolean())).thenReturn(List.of(soup, fish, beer));
        when(mapper.listCandidateAllergens(List.of(68L, 51L, 48L))).thenReturn(List.of());
        when(mapper.listCandidateIngredients(List.of(68L, 51L, 48L))).thenReturn(List.of());
        when(mapper.listCandidateAdaptations(List.of(68L, 51L, 48L))).thenReturn(List.of(
                adaptation(68L, 0, 1, 1, "WARM", "EASY", "NONE", "FALSE", "LIGHT"),
                adaptation(51L, 3, 4, 5, "WARM", "HARD", "NONE", "TRUE", "RICH"),
                adaptation(48L, 0, 0, 0, "COLD", "EASY", "CONTAINS", "FALSE", "LIGHT")));
        when(mapper.listSeasonalMatches(any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.listActiveRuleSets()).thenReturn(List.of());

        DietRecommendationVO result = service.recommend(7L, "diet-test-cold", request, "trace-cold");

        assertThat(result.getItems()).extracting(DietRecommendationVO.Item::getName)
                .containsExactly("鸡蛋汤");
        assertThat(result.getExcludedItems()).extracting(DietRecommendationVO.ExcludedItem::getName)
                .containsExactlyInAnyOrder("老坛酸菜鱼", "雪花啤酒");
    }

    private DietRecommendationDTO request() {
        DietRecommendationDTO request = new DietRecommendationDTO();
        request.setScene("GENERAL");
        request.setUseSavedProfile(false);
        return request;
    }

    private Map<String, Object> candidate(long id) {
        Map<String, Object> value = new HashMap<>();
        value.put("productType", "dish"); value.put("productId", id); value.put("name", "测试菜品");
        value.put("price", new BigDecimal("28.00")); value.put("profileVersion", 1); value.put("recipeVersion", 1);
        value.put("energyKcal", new BigDecimal("350")); value.put("proteinG", new BigDecimal("25"));
        value.put("fatG", new BigDecimal("10")); value.put("carbohydrateG", new BigDecimal("30"));
        value.put("dietaryFiberG", new BigDecimal("6")); value.put("sugarG", new BigDecimal("3"));
        value.put("sodiumMg", new BigDecimal("420"));
        return value;
    }

    private Map<String, Object> adaptation(long id, int spicy, int oil, int salt,
                                           String temperature, String digestibility,
                                           String alcohol, String pickled, String soupBase) {
        return Map.of("dishId", id, "spicyLevel", spicy, "oilLevel", oil,
                "saltLevel", salt, "temperatureType", temperature,
                "digestibility", digestibility, "alcoholContent", alcohol,
                "pickledFood", pickled, "soupBaseType", soupBase);
    }
}
