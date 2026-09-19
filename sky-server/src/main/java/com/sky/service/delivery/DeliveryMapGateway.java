package com.sky.service.delivery;

import static com.sky.service.delivery.DeliveryModels.*;

/** 地图提供方隔离接口。 */
public interface DeliveryMapGateway {
    GeocodeResult geocode(String fullAddress);
    DistanceResult drivingDistance(Coordinate origin, Coordinate destination);
    String provider();
}
