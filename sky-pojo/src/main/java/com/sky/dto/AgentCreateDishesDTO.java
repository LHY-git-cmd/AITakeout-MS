package com.sky.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Agent专用新增参数：允许稍后上传图片，不改变手工新增接口的图片必填规则。 */
@Data
public class AgentCreateDishesDTO {
    @NotNull private Boolean testData;
    @Valid @NotEmpty @Size(max = 10) private List<@NotNull Item> dishes;

    @Data
    public static class Item {
        @NotBlank @Size(max = 64) private String name;
        @NotNull @Positive private Long categoryId;
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) private BigDecimal price;
        @Size(max = 255) private String image;
        @Size(max = 255) private String description;
        @Valid @Size(max = 20) private List<@NotNull Flavor> flavors = new ArrayList<>();
        @Valid private DishNutritionDTO nutrition;
    }

    @Data
    public static class Flavor {
        @NotBlank @Size(max = 32) private String name;
        @NotBlank @Size(max = 255) private String value;
    }
}
