package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单预览视图对象")
public class OrderPreviewVO implements Serializable {
    @Schema(description = "商品金额（分）")
    private Long goodsAmountCent;
    @Schema(description = "打包费（分）")
    private Long packAmountCent;
    @Schema(description = "配送费（分）")
    private Long deliveryFeeCent;
    @Schema(description = "折扣金额（分）")
    private Long discountAmountCent;
    @Schema(description = "总计金额（分）")
    private Long amountCent;
    @Schema(description = "配送距离（米）")
    private Integer distanceMeters;
    @Schema(description = "计价规则版本")
    private String pricingRuleVersion;
    @Schema(description = "预计送达时间")
    private LocalDateTime estimatedDeliveryTime;
    @Schema(description = "可用配送时段")
    private List<DeliverySlotVO> availableSlots;
    @Schema(description = "结算商品项")
    private List<CheckoutItemVO> items;
    @Schema(description = "预览令牌")
    private String previewToken;
    @Schema(description = "过期时间")
    private LocalDateTime expiresAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "配送时段视图对象")
    public static class DeliverySlotVO implements Serializable {
        @Schema(description = "起始时间")
        private LocalDateTime start;
        @Schema(description = "结束时间")
        private LocalDateTime end;
        @Schema(description = "标签")
        private String label;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @Schema(description = "结算商品项视图对象")
    public static class CheckoutItemVO implements Serializable {
        @Schema(description = "菜品ID")
        private Long dishId;
        @Schema(description = "套餐ID")
        private Long setmealId;
        @Schema(description = "名称")
        private String name;
        @Schema(description = "口味")
        private String flavor;
        @Schema(description = "数量")
        private Integer quantity;
        @Schema(description = "单价（分）")
        private Long unitPriceCent;
        @Schema(description = "小计（分）")
        private Long subtotalCent;
        @Schema(description = "图片")
        private String image;
    }
}