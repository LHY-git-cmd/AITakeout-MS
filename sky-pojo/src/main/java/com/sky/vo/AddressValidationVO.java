package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "地址配送预检视图对象")
public class AddressValidationVO implements Serializable {
    @Schema(description = "地址ID")
    private Long addressId;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "是否可配送")
    private Boolean deliverable;
    @Schema(description = "距离（米）")
    private Integer distanceMeters;
    @Schema(description = "配送费（分）")
    private Long deliveryFeeCent;
    @Schema(description = "地图服务提供商")
    private String mapProvider;
    @Schema(description = "规则版本")
    private String ruleVersion;
    @Schema(description = "消息")
    private String message;
    @Schema(description = "验证时间")
    private LocalDateTime validatedAt;
}