package com.sky.service.delivery;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryMapContractTest {
    private final MockMapGateway gateway = new MockMapGateway();
    private final DeliveryModels.Coordinate shop = new DeliveryModels.Coordinate(BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void mockAddressesHaveDeterministicDistances() {
        assertThat(distance("测试路 1 号")).isEqualTo(1500);
        assertThat(distance("测试路 2 号")).isEqualTo(4200);
        assertThat(distance("测试路 3 号")).isEqualTo(6500);
    }

    @Test
    void distinguishesInvalidAddressFromTimeout() {
        assertThat(gateway.geocode("无效地址").errorCode()).isEqualTo("GEOCODE_FAILED");
        assertThat(gateway.geocode("超时地址").errorCode()).isEqualTo("MAP_TIMEOUT");
    }

    private int distance(String address) {
        var geocode = gateway.geocode(address);
        return gateway.drivingDistance(shop, geocode.coordinate()).distanceMeters();
    }
}
