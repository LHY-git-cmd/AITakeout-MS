package com.sky.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 可解释的推荐结果；不包含模型思维过程。 */
@Data
@Builder
public class DietRecommendationVO {
    private String recommendationId;
    private String riskLevel;
    private String status;
    private List<String> notices;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private String productType;
        private Long productId;
        private String name;
        private BigDecimal price;
        private String image;
        private BigDecimal score;
        private Map<String, BigDecimal> nutrition;
        private List<String> matchReasons;
        private List<String> warnings;
        private List<String> ruleSources;
    }
}
