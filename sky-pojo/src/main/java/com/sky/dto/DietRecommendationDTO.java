package com.sky.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 个性化饮食推荐请求；健康字段均为受控编码而不是自由文本诊断。 */
@Data
public class DietRecommendationDTO {
    @NotBlank
    private String scene;
    @Min(1) @Max(20)
    private int peopleCount = 1;
    @DecimalMin("0")
    private BigDecimal budget;
    @Size(max = 32)
    private String mealType;
    @Size(max = 32)
    private String regionCode;
    private boolean useSavedProfile = true;
    private List<String> allergens = new ArrayList<>();
    private List<String> excludedIngredients = new ArrayList<>();
    private List<String> goals = new ArrayList<>();
    private List<String> conditions = new ArrayList<>();
    private List<String> preferences = new ArrayList<>();
    private List<String> hardConstraints = new ArrayList<>();
    private List<String> softPreferences = new ArrayList<>();
    private String season;
    @DecimalMin("0")
    private BigDecimal confidence = new BigDecimal("0.95");
    @Min(1) @Max(20)
    private int limit = 5;
}
