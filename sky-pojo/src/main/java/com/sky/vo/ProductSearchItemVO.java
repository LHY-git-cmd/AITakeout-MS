package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 菜品与套餐统一搜索结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchItemVO {
    private Long id;
    private String productType;
    private String name;
    private BigDecimal price;
    private String image;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Integer relevanceScore;
    private Boolean hasFlavor;

    public String getStableKey() {
        return productType + ":" + id;
    }
}
