package com.sky.service.delivery;

import com.sky.properties.DeliveryProperties;
import org.springframework.stereotype.Service;

import static com.sky.service.delivery.DeliveryModels.DeliveryQuote;

/** 按配置的距离区间计算配送费。 */
@Service
public class DeliveryPricingService {
    private final DeliveryProperties properties;
    public DeliveryPricingService(DeliveryProperties properties) { this.properties = properties; }

    public DeliveryQuote quote(int distanceMeters) {
        if (distanceMeters < 0 || distanceMeters > properties.getMaxDistanceMeters()) {
            return new DeliveryQuote(false, 0, distanceMeters, properties.getPricingRuleVersion(), "超出配送范围");
        }
        long fee = distanceMeters <= properties.getNearDistanceMeters()
                ? properties.getNearFeeCent() : properties.getFarFeeCent();
        return new DeliveryQuote(true, fee, distanceMeters, properties.getPricingRuleVersion(), "可配送");
    }
}
