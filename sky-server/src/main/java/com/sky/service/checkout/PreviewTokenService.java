package com.sky.service.checkout;

import com.sky.properties.DeliveryProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/** 对用户确认的试算摘要签名，防止客户端篡改并检测过期。 */
@Service
public class PreviewTokenService {
    private final DeliveryProperties properties;
    public PreviewTokenService(DeliveryProperties properties) { this.properties = properties; }

    public String issue(long userId, String cartDigest, long addressId, String mode, LocalDateTime slot, LocalDateTime expiresAt) {
        String payload = userId + "|" + cartDigest + "|" + addressId + "|" + mode + "|"
                + (slot == null ? "" : slot) + "|" + expiresAt.toEpochSecond(ZoneOffset.UTC);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public void verify(String token, long userId, String cartDigest, long addressId, String mode, LocalDateTime slot) {
        if (token == null || !token.contains(".")) throw new IllegalArgumentException("试算凭证缺失，请重新试算");
        String[] parts = token.split("\\.", 2);
        if (!constantTimeEquals(sign(parts[0]), parts[1])) throw new IllegalArgumentException("试算凭证无效，请重新试算");
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split("\\|", -1);
        String requestedSlot = slot == null ? "" : slot.toString();
        if (values.length != 6 || !values[0].equals(String.valueOf(userId)) || !values[1].equals(cartDigest)
                || !values[2].equals(String.valueOf(addressId)) || !values[3].equals(mode) || !values[4].equals(requestedSlot)) {
            throw new IllegalArgumentException("结算信息已变化，请重新确认");
        }
        if (Long.parseLong(values[5]) < LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)) {
            throw new IllegalArgumentException("试算已过期，请重新试算");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getPreviewSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("无法签发试算凭证", exception); }
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
