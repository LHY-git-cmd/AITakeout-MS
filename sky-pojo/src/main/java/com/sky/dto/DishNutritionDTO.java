package com.sky.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
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
    @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
    private BigDecimal servingSizeG;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal energyKcal;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal proteinG;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal fatG;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal carbohydrateG;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal dietaryFiberG;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal sugarG;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal sodiumMg;
    @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal purineMg;
    @NotBlank
    @Size(max = 32) private String sourceType;
    @NotBlank
    @Size(max = 500) private String sourceReference;
    @Size(max = 128) private String calculationMethod;
    @Size(max = 500) private String uncertaintyNote;
    @Valid private List<@NotNull IngredientItem> ingredients = new ArrayList<>();
    @Valid private List<@NotNull AllergenItem> allergens = new ArrayList<>();

    /** 食材统一使用标准编码，避免依赖自由文本做安全判断。 */
    @Data
    public static class IngredientItem {
        @NotBlank @Size(max = 64) private String code;
        @NotBlank @Size(max = 128) private String name;
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) private BigDecimal amountG;
        private String roleType = "PRIMARY";
        private boolean replaceable;
    }

    /** 声明状态仅允许 FREE/CONTAINS/MAY_CONTAIN/CROSS_CONTACT_RISK/UNKNOWN。 */
    @Data
    public static class AllergenItem {
        @NotBlank @Size(max = 64) private String code;
        @NotBlank private String status;
        @NotBlank @Size(max = 500) private String sourceReference;
    }
}
