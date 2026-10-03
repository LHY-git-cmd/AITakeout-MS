package com.sky.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 管理端维护的菜品营养、食材与过敏原版本。 */
@Data
public class DishNutritionDTO {
    @Min(1)
    private int profileVersion = 1;
    @Min(1)
    private int recipeVersion = 1;
    @NotNull @DecimalMin("0.01")
    private BigDecimal servingSizeG;
    @DecimalMin("0") private BigDecimal energyKcal;
    @DecimalMin("0") private BigDecimal proteinG;
    @DecimalMin("0") private BigDecimal fatG;
    @DecimalMin("0") private BigDecimal carbohydrateG;
    @DecimalMin("0") private BigDecimal dietaryFiberG;
    @DecimalMin("0") private BigDecimal sugarG;
    @DecimalMin("0") private BigDecimal sodiumMg;
    @DecimalMin("0") private BigDecimal purineMg;
    @NotBlank
    private String sourceType;
    @NotBlank
    private String sourceReference;
    private String calculationMethod;
    private String uncertaintyNote;
    private List<IngredientItem> ingredients = new ArrayList<>();
    private List<AllergenItem> allergens = new ArrayList<>();

    /** 食材统一使用标准编码，避免依赖自由文本做安全判断。 */
    @Data
    public static class IngredientItem {
        @NotBlank private String code;
        @NotBlank private String name;
        @DecimalMin("0") private BigDecimal amountG;
        private String roleType = "PRIMARY";
        private boolean replaceable;
    }

    /** 声明状态仅允许 FREE/CONTAINS/MAY_CONTAIN/CROSS_CONTACT_RISK/UNKNOWN。 */
    @Data
    public static class AllergenItem {
        @NotBlank private String code;
        @NotBlank private String status;
        @NotBlank private String sourceReference;
    }
}
