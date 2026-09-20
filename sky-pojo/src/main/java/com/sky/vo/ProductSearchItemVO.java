package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "产品搜索项视图对象")
public class ProductSearchItemVO {
    @Schema(description = "ID")
    private Long id;
    @Schema(description = "产品类型")
    private String productType;
    @Schema(description = "名称")
    private String name;
    @Schema(description = "价格")
    private BigDecimal price;
    @Schema(description = "图片")
    private String image;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "分类ID")
    private Long categoryId;
    @Schema(description = "分类名称")
    private String categoryName;
    @Schema(description = "相关性分数")
    private Integer relevanceScore;
    @Schema(description = "是否有口味选项")
    private Boolean hasFlavor;

    public String getStableKey() {
        return productType + ":" + id;
    }
}