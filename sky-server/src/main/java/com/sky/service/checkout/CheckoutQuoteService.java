package com.sky.service.checkout;

import com.sky.dto.OrderPreviewDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.properties.DeliveryProperties;
import com.sky.service.order.DeliveryRangeService;
import com.sky.vo.OrderPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static com.sky.service.checkout.CheckoutModels.*;

/** 从当前数据库商品、购物车和配送规则生成唯一权威试算。 */
@Service
@RequiredArgsConstructor
public class CheckoutQuoteService {
    private final ShoppingCartMapper shoppingCartMapper;
    private final AddressBookMapper addressBookMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;
    private final DeliveryRangeService deliveryRangeService;
    private final DeliverySlotService deliverySlotService;
    private final PreviewTokenService tokenService;
    private final DeliveryProperties properties;

    public OrderPreviewVO preview(long userId, OrderPreviewDTO request) { return quote(userId, request, true).preview(); }

    public QuoteSnapshot quote(long userId, OrderPreviewDTO request, boolean issueToken) {
        if (request == null || request.getAddressBookId() == null) throw new AddressBookBusinessException("收货地址不能为空");
        AddressBook address = addressBookMapper.getByIdAndUserId(request.getAddressBookId(), userId);
        if (address == null) throw new AddressBookBusinessException("收货地址不存在");
        DeliveryRangeService.ValidationResult delivery = deliveryRangeService.validate(address);
        if (!delivery.success() || !delivery.deliverable()) throw new OrderBusinessException(delivery.message());
        // 将本次权威校验结果写入内存快照，供订单保存地图与坐标信息。
        address.setLatitude(delivery.coordinate().latitude());
        address.setLongitude(delivery.coordinate().longitude());
        address.setMapProvider(delivery.provider());
        address.setDistanceMeters(delivery.distanceMeters());
        address.setDeliverable(true);

        List<ShoppingCart> raw = shoppingCartMapper.listByUserId(userId);
        if (CollectionUtils.isEmpty(raw)) throw new ShoppingCartBusinessException("购物车为空");
        List<ShoppingCart> snapshot = new ArrayList<>();
        List<OrderPreviewVO.CheckoutItemVO> items = new ArrayList<>();
        long goodsCent = 0;
        int totalCount = 0;
        for (ShoppingCart cart : raw) {
            Product product = currentProduct(cart);
            int quantity = cart.getNumber() == null ? 0 : cart.getNumber();
            if (quantity <= 0) throw new OrderBusinessException("购物车商品数量无效");
            long unitCent = cents(product.price());
            goodsCent = Math.addExact(goodsCent, Math.multiplyExact(unitCent, quantity));
            totalCount = Math.addExact(totalCount, quantity);
            ShoppingCart authoritative = ShoppingCart.builder().id(cart.getId()).userId(userId).dishId(cart.getDishId())
                    .setmealId(cart.getSetmealId()).dishFlavor(cart.getDishFlavor()).number(quantity)
                    .amount(product.price()).name(product.name()).image(product.image()).createTime(cart.getCreateTime()).build();
            snapshot.add(authoritative);
            items.add(OrderPreviewVO.CheckoutItemVO.builder().dishId(cart.getDishId()).setmealId(cart.getSetmealId())
                    .name(product.name()).flavor(cart.getDishFlavor()).quantity(quantity).unitPriceCent(unitCent)
                    .subtotalCent(unitCent * quantity).image(product.image()).build());
        }

        long packCent = totalCount * 100L;
        long amountCent = goodsCent + packCent + delivery.feeCent();
        LocalDateTime now = LocalDateTime.now();
        List<DeliverySlot> slots = deliverySlotService.availableSlots(now, delivery.distanceMeters());
        String mode = "SCHEDULED".equalsIgnoreCase(request.getDeliveryMode()) ? "SCHEDULED" : "IMMEDIATE";
        DeliverySlot selected = "SCHEDULED".equals(mode)
                ? deliverySlotService.requireAvailable(request.getDeliverySlotStart(), slots) : null;
        LocalDateTime estimated = selected == null ? deliverySlotService.earliest(now, delivery.distanceMeters()) : selected.end();
        LocalDateTime expiresAt = now.plusSeconds(properties.getPreviewTtlSeconds());
        String digest = digest(snapshot);
        String token = issueToken ? tokenService.issue(userId, digest, address.getId(), mode,
                selected == null ? null : selected.start(), expiresAt) : null;

        OrderPreviewVO preview = OrderPreviewVO.builder().goodsAmountCent(goodsCent).packAmountCent(packCent)
                .deliveryFeeCent(delivery.feeCent()).discountAmountCent(0L).amountCent(amountCent)
                .distanceMeters(delivery.distanceMeters()).pricingRuleVersion(delivery.ruleVersion())
                .estimatedDeliveryTime(estimated).availableSlots(slots.stream().map(slot ->
                        OrderPreviewVO.DeliverySlotVO.builder().start(slot.start()).end(slot.end()).label(slot.label()).build()).toList())
                .items(items).previewToken(token).expiresAt(expiresAt).build();
        return new QuoteSnapshot(preview, address, snapshot, digest, selected == null ? null : selected.start(),
                selected == null ? null : selected.end());
    }

    private Product currentProduct(ShoppingCart cart) {
        if (cart.getDishId() != null) {
            Dish dish = dishMapper.getById(cart.getDishId());
            if (dish == null || !Integer.valueOf(1).equals(dish.getStatus())) throw new OrderBusinessException("商品已下架：" + cart.getName());
            return new Product(dish.getName(), dish.getPrice(), dish.getImage());
        }
        if (cart.getSetmealId() != null) {
            Setmeal setmeal = setmealMapper.getById(cart.getSetmealId());
            if (setmeal == null || !Integer.valueOf(1).equals(setmeal.getStatus())) throw new OrderBusinessException("套餐已下架：" + cart.getName());
            return new Product(setmeal.getName(), setmeal.getPrice(), setmeal.getImage());
        }
        throw new OrderBusinessException("购物车包含无效商品");
    }

    private long cents(BigDecimal value) {
        if (value == null) throw new OrderBusinessException("商品价格缺失");
        try { return value.movePointRight(2).longValueExact(); }
        catch (ArithmeticException exception) { throw new OrderBusinessException("商品价格精度无效"); }
    }

    private String digest(List<ShoppingCart> carts) {
        try {
            StringBuilder value = new StringBuilder();
            for (ShoppingCart cart : carts) value.append(cart.getDishId()).append(':').append(cart.getSetmealId())
                    .append(':').append(cart.getDishFlavor()).append(':').append(cart.getNumber()).append(':')
                    .append(cart.getAmount()).append(';');
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record Product(String name, BigDecimal price, String image) { }
}
