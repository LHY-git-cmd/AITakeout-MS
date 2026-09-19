package com.sky.auth;

/** Request metadata used when creating or revoking a user session. */
public record AuthClientContext(String ipAddress, String userAgent, String deviceId) {
    public AuthClientContext {
        ipAddress = ipAddress == null ? "" : ipAddress;
        userAgent = userAgent == null ? "" : userAgent;
        deviceId = deviceId == null || deviceId.isBlank() ? "unknown" : deviceId;
    }
}
