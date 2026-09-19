package com.sky.vo;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/** 地址配送预检结果。 */
@Data
@Builder
public class AddressValidationVO {
    private Long addressId;
    private String status;
    private Boolean deliverable;
    private Integer distanceMeters;
    private Long deliveryFeeCent;
    private String mapProvider;
    private String ruleVersion;
    private String message;
    private LocalDateTime validatedAt;
}
