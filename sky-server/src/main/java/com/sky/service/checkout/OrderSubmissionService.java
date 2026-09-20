package com.sky.service.checkout;

import com.sky.entity.OrderSubmission;
import com.sky.mapper.OrderSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 管理用户级下单幂等占位与请求内容一致性。 */
@Service
@RequiredArgsConstructor
public class OrderSubmissionService {
    private final OrderSubmissionMapper mapper;

    public Reservation reserve(long userId, String key, String requestHash) {
        String normalized = normalize(key);
        OrderSubmission candidate = OrderSubmission.builder().userId(userId).idempotencyKey(normalized)
                .requestHash(requestHash).createTime(LocalDateTime.now()).build();
        boolean created = mapper.insertIgnore(candidate) == 1;
        OrderSubmission persisted = created ? candidate : mapper.find(userId, normalized);
        if (persisted == null) throw new IllegalStateException("下单幂等记录创建失败");
        if (!requestHash.equals(persisted.getRequestHash())) throw new IllegalArgumentException("相同幂等键不能用于不同订单内容");
        return new Reservation(persisted, created);
    }

    public void attachOrder(long reservationId, long orderId) {
        if (mapper.attachOrder(reservationId, orderId) != 1) throw new IllegalStateException("下单幂等记录更新失败");
    }

    private String normalize(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key 不能为空");
        String value = key.trim();
        if (value.length() > 80) throw new IllegalArgumentException("Idempotency-Key 不能超过80个字符");
        return value;
    }

    public record Reservation(OrderSubmission submission, boolean created) { }
}
