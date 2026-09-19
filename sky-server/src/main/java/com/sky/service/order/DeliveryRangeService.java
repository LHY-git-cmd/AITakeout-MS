package com.sky.service.order;

import com.sky.entity.AddressBook;
import com.sky.exception.OrderBusinessException;
import com.sky.properties.DeliveryProperties;
import com.sky.service.delivery.DeliveryMapGateway;
import com.sky.service.delivery.DeliveryPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.sky.service.delivery.DeliveryModels.*;

/** 组合地图解析、驾车距离和分段计价，确保所有环境都执行配送校验。 */
@Service
@RequiredArgsConstructor
public class DeliveryRangeService {
    private final DeliveryMapGateway mapGateway;
    private final DeliveryPricingService pricingService;
    private final DeliveryProperties properties;

    public ValidationResult validate(AddressBook addressBook) {
        GeocodeResult destination = mapGateway.geocode(fullAddress(addressBook));
        if (!destination.success()) return ValidationResult.failure(destination.provider(), destination.errorCode(), destination.message());
        GeocodeResult origin = mapGateway.geocode(properties.getShopAddress());
        if (!origin.success()) return ValidationResult.failure(origin.provider(), "SHOP_GEOCODE_FAILED", "门店地址解析失败");
        DistanceResult distance = mapGateway.drivingDistance(origin.coordinate(), destination.coordinate());
        if (!distance.success()) return ValidationResult.failure(distance.provider(), distance.errorCode(), distance.message());
        DeliveryQuote quote = pricingService.quote(distance.distanceMeters());
        return new ValidationResult(true, destination.coordinate(), distance.provider(), distance.distanceMeters(),
                quote.deliverable(), quote.feeCent(), quote.ruleVersion(), quote.deliverable() ? null : "OUT_OF_RANGE", quote.message());
    }

    public DeliveryQuote check(AddressBook addressBook) {
        ValidationResult result = validate(addressBook);
        if (!result.success() || !result.deliverable()) throw new OrderBusinessException(result.message());
        return new DeliveryQuote(true, result.feeCent(), result.distanceMeters(), result.ruleVersion(), "可配送");
    }

    public String fullAddress(AddressBook addressBook) {
        return safe(addressBook.getProvinceName()) + safe(addressBook.getCityName())
                + safe(addressBook.getDistrictName()) + safe(addressBook.getDetail());
    }

    private String safe(String value) { return value == null ? "" : value; }

    public record ValidationResult(boolean success, Coordinate coordinate, String provider, int distanceMeters,
                                   boolean deliverable, long feeCent, String ruleVersion,
                                   String errorCode, String message) {
        static ValidationResult failure(String provider, String errorCode, String message) {
            return new ValidationResult(false, null, provider, 0, false, 0, null, errorCode, message);
        }
    }
}
