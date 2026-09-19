package com.sky.service.checkout;

import com.sky.entity.AddressBook;
import com.sky.entity.ShoppingCart;
import com.sky.vo.OrderPreviewVO;

import java.time.LocalDateTime;
import java.util.List;

/** 结算域内部模型。 */
public final class CheckoutModels {
    private CheckoutModels() { }
    public record DeliverySlot(LocalDateTime start, LocalDateTime end, String label) { }
    public record QuoteSnapshot(OrderPreviewVO preview, AddressBook address, List<ShoppingCart> cartSnapshot,
                                String cartDigest, LocalDateTime slotStart, LocalDateTime slotEnd) { }
}
