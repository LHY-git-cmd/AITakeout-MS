package com.sky.service.delivery;

import java.math.BigDecimal;

/** 配送地图适配器使用的不可变值对象。 */
public final class DeliveryModels {
    private DeliveryModels() { }

    public record Coordinate(BigDecimal latitude, BigDecimal longitude) { }
    public record GeocodeResult(boolean success, Coordinate coordinate, String provider,
                                String errorCode, String message) { }
    public record DistanceResult(boolean success, int distanceMeters, String provider,
                                 String errorCode, String message) { }
    public record DeliveryQuote(boolean deliverable, long feeCent, int distanceMeters,
                                String ruleVersion, String message) { }
}
