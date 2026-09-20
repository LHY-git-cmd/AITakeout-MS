package com.sky.service.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Development SMS gateway; never exposes a code through an HTTP controller. */
@Component
@Profile({"dev", "test"})
public class MockSmsGateway implements SmsGateway {
    public static final String DEV_CODE = "246810";
    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sendCounts = new ConcurrentHashMap<>();

    @Override
    public String sendCode(String phone, String purpose) {
        codes.put(key(phone, purpose), DEV_CODE);
        sendCounts.computeIfAbsent(key(phone, purpose), ignored -> new AtomicInteger()).incrementAndGet();
        return DEV_CODE;
    }

    public String codeFor(String phone, String purpose) {
        return codes.get(key(phone, purpose));
    }

    public int sendCountFor(String phone, String purpose) {
        AtomicInteger count = sendCounts.get(key(phone, purpose));
        return count == null ? 0 : count.get();
    }

    private String key(String phone, String purpose) { return purpose + ":" + phone; }
}
