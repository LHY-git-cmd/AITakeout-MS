package com.sky.logging;

import com.fasterxml.jackson.core.JsonStreamContext;
import net.logstash.logback.mask.ValueMasker;

import java.util.regex.Pattern;

/** 日志输出前遮盖凭据及手机号；同样应用于异常文本，不改变业务响应。 */
public class SensitiveDataMasker implements ValueMasker {
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)([\\\"']?(?:password|passwd|initialPassword|api[_-]?key|access[_-]?token|refresh[_-]?token|token|secret|authorization|cookie)[\\\"']?\\s*[:=]\\s*)(?:[\\\"][^\\\"]*[\\\"]|'[^']*'|[^\\s,;)}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s\\\"',;]+[=]*");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern API_KEY = Pattern.compile("(?<![A-Za-z0-9])(?:sk-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16})");
    private static final Pattern PERSONAL_NUMBER = Pattern.compile("(?<!\\d)(?:[1-9]\\d{16}[\\dXx]|1[3-9]\\d{9})(?!\\d)");

    @Override
    public Object mask(JsonStreamContext context, Object value) {
        if (!(value instanceof String text)) return null;
        String masked = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        masked = CREDENTIAL.matcher(masked).replaceAll("$1[REDACTED]");
        masked = JWT.matcher(masked).replaceAll("[REDACTED]");
        masked = API_KEY.matcher(masked).replaceAll("[REDACTED]");
        return PERSONAL_NUMBER.matcher(masked).replaceAll("[REDACTED]");
    }
}
