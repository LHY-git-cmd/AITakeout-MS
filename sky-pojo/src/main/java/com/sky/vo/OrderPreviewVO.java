package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 服务端权威试算结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPreviewVO {
    private Long goodsAmountCent;
    private Long packAmountCent;
    private Long deliveryFeeCent;
    private Long discountAmountCent;
    private Long amountCent;
    private Integer distanceMeters;
    private String pricingRuleVersion;
    private LocalDateTime estimatedDeliveryTime;
    private List<DeliverySlotVO> availableSlots;
    private List<CheckoutItemVO> items;
    private String previewToken;
    private LocalDateTime expiresAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeliverySlotVO {
        private LocalDateTime start;
        private LocalDateTime end;
        private String label;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CheckoutItemVO {
        private Long dishId;
        private Long setmealId;
        private String name;
        private String flavor;
        private Integer quantity;
        private Long unitPriceCent;
        private Long subtotalCent;
        private String image;
    }
}
