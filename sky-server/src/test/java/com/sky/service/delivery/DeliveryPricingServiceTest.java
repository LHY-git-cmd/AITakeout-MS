package com.sky.service.delivery;

import com.sky.properties.DeliveryProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryPricingServiceTest {
    private final DeliveryPricingService service = new DeliveryPricingService(new DeliveryProperties());

    @ParameterizedTest
    @CsvSource({"3000,500,true", "3001,800,true", "5000,800,true", "5001,0,false"})
    void pricesDistanceBoundaries(int meters, long fee, boolean deliverable) {
        var quote = service.quote(meters);
        assertThat(quote.feeCent()).isEqualTo(fee);
        assertThat(quote.deliverable()).isEqualTo(deliverable);
    }
}
