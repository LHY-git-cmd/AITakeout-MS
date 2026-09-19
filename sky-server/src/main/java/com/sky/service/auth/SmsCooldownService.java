package com.sky.service.auth;

import com.sky.mapper.SmsVerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Atomically reserves the per-phone SMS cooldown before calling an external gateway. */
@Service
@RequiredArgsConstructor
public class SmsCooldownService {
    private final SmsVerificationMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(String phone, String purpose, LocalDateTime now, LocalDateTime nextAllowedAt) {
        if (mapper.insertCooldown(phone, purpose, nextAllowedAt) == 1) return true;
        return mapper.advanceCooldown(phone, purpose, now, nextAllowedAt) == 1;
    }
}
