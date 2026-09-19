package com.sky.service.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.sky.service.delivery.DeliveryModels.*;

/** 开发和自动化测试专用的确定性地图实现。 */
@Component
@ConditionalOnProperty(name = "sky.delivery.map-provider", havingValue = "mock")
public class MockMapGateway implements DeliveryMapGateway {
    @Override
    public GeocodeResult geocode(String address) {
        if (address == null || address.isBlank() || address.contains("无效地址")) {
            return new GeocodeResult(false, null, provider(), "GEOCODE_FAILED", "无法识别该地址");
        }
        if (address.contains("超时地址")) {
            return new GeocodeResult(false, null, provider(), "MAP_TIMEOUT", "地图服务暂时不可用，请重试");
        }
        int distance = address.contains("测试路 3 号") ? 6500
                : address.contains("测试路 2 号") ? 4200
                : address.contains("测试路 1 号") ? 1500 : 1500;
        // 用合法经纬度的小数偏移编码固定距离，便于同时写入真实坐标字段。
        return new GeocodeResult(true, new Coordinate(
                BigDecimal.valueOf(39).add(BigDecimal.valueOf(distance, 6)), BigDecimal.valueOf(116)),
                provider(), null, null);
    }

    @Override
    public DistanceResult drivingDistance(Coordinate origin, Coordinate destination) {
        int distance = destination == null ? 0 : destination.latitude().subtract(BigDecimal.valueOf(39))
                .movePointRight(6).intValue();
        return new DistanceResult(destination != null, distance, provider(),
                destination == null ? "ROUTE_FAILED" : null, destination == null ? "无法规划配送路线" : null);
    }

    @Override public String provider() { return "mock"; }
}
