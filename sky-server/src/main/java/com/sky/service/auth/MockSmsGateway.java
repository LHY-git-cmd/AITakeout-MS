package com.sky.service.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Development SMS gateway; never exposes a code through an HTTP controller. */
@Component
@Profile({"dev", "test"})
public class MockSmsGateway implements SmsGateway {
    public static final String DEV_CODE = "246810";
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @Override
    public String sendCode(String phone, String purpose) {
        codes.put(key(phone, purpose), DEV_CODE);
        return DEV_CODE;
    }

    public String codeFor(String phone, String purpose) {
        return codes.get(key(phone, purpose));
    }

    private String key(String phone, String purpose) { return purpose + ":" + phone; }
}
