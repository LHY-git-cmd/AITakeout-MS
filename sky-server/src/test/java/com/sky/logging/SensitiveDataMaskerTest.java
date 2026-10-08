package com.sky.logging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 保证消息和异常中的认证信息不会原样进入日志，诊断状态仍可读取。 */
class SensitiveDataMaskerTest {
    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void masksCredentialsInMessagesAndUrls() {
        String token = "eyJ" + "testheader.testpayload.testsignature";
        String result = (String) masker.mask(null,
                "GET /ws/client?token=" + token + " password='test password' status=401 sk-" + "x".repeat(30));
        assertFalse(result.contains(token));
        assertFalse(result.contains("test password"));
        assertFalse(result.contains("x".repeat(30)));
        assertTrue(result.contains("status=401"));
    }

    @Test
    void masksPersonalNumbersAndBearerHeaders() {
        String result = (String) masker.mask(null, "Authorization: Bearer test-token phone=13800138000");
        assertFalse(result.contains("test-token"));
        assertFalse(result.contains("13800138000"));
    }

    @Test
    void leavesNonStringMetricsUntouched() {
        assertNull(masker.mask(null, 42));
        assertEquals("operation completed", masker.mask(null, "operation completed"));
    }
}
