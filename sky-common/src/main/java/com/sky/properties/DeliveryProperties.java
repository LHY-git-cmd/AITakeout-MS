package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 配送地图、计价和试算有效期配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "sky.delivery")
public class DeliveryProperties {
    private String mapProvider;
    private String shopAddress;
    private String baiduAk;
    private int nearDistanceMeters = 3000;
    private int maxDistanceMeters = 5000;
    private long nearFeeCent = 500;
    private long farFeeCent = 800;
    private int previewTtlSeconds = 300;
    private String pricingRuleVersion = "delivery-v1";
    private String previewSecret = "development-checkout-preview-secret-change-me";
}
