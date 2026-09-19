package com.sky.service.checkout;

import com.sky.dto.OrderPreviewDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.*;
import com.sky.properties.DeliveryProperties;
import com.sky.service.delivery.DeliveryModels;
import com.sky.service.order.DeliveryRangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutQuoteServiceTest {
    @Mock ShoppingCartMapper cartMapper;
    @Mock AddressBookMapper addressMapper;
    @Mock DishMapper dishMapper;
    @Mock SetmealMapper setmealMapper;
    @Mock DeliveryRangeService deliveryRangeService;
    CheckoutQuoteService service;

    @BeforeEach
    void setUp() {
        DeliveryProperties properties = new DeliveryProperties();
        properties.setPreviewSecret("test-checkout-preview-secret-at-least-32-chars");
        service = new CheckoutQuoteService(cartMapper, addressMapper, dishMapper, setmealMapper,
                deliveryRangeService, new DeliverySlotService(), new PreviewTokenService(properties), properties);
    }

    @Test
    void quoteUsesCurrentProductsAndDistanceFee() {
        AddressBook address = AddressBook.builder().id(3L).userId(7L).build();
        when(addressMapper.getByIdAndUserId(3L, 7L)).thenReturn(address);
        when(deliveryRangeService.validate(address)).thenReturn(new DeliveryRangeService.ValidationResult(true,
                new DeliveryModels.Coordinate(BigDecimal.ONE, BigDecimal.ONE), "mock", 4200,
                true, 800, "delivery-v1", null, "可配送"));
        when(cartMapper.listByUserId(7L)).thenReturn(List.of(ShoppingCart.builder()
                .id(1L).userId(7L).dishId(9L).number(1).amount(BigDecimal.ONE).build()));
        when(dishMapper.getById(9L)).thenReturn(Dish.builder().id(9L).name("鱼香肉丝")
                .price(new BigDecimal("79.00")).status(1).build());
        OrderPreviewDTO request = new OrderPreviewDTO();
        request.setAddressBookId(3L);

        var quote = service.preview(7L, request);

        assertThat(quote.getGoodsAmountCent()).isEqualTo(7900);
        assertThat(quote.getPackAmountCent()).isEqualTo(100);
        assertThat(quote.getDeliveryFeeCent()).isEqualTo(800);
        assertThat(quote.getAmountCent()).isEqualTo(8800);
        assertThat(quote.getPreviewToken()).isNotBlank();
        assertThat(quote.getAvailableSlots()).allSatisfy(slot ->
                assertThat(slot.getEnd()).isEqualTo(slot.getStart().plusMinutes(30)));
    }
}
