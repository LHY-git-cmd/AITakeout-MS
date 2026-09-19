package com.sky.service.auth;

/** Abstraction over an SMS provider. */
public interface SmsGateway {
    String sendCode(String phone, String purpose);
}
