package com.sky.service.auth;

import com.sky.mapper.SmsVerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Atomically reserves the per-phone SMS cooldown before calling an external gateway. */
@Service
@RequiredArgsConstructor
public class SmsCooldownService {
    private final SmsVerificationMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(String phone, String purpose, LocalDateTime now, LocalDateTime nextAllowedAt) {
        String reservationId = UUID.randomUUID().toString();
        if (mapper.insertCooldown(phone, purpose, reservationId, nextAllowedAt) == 1
                || mapper.advanceCooldown(phone, purpose, reservationId, now, nextAllowedAt) == 1) {
            return new Reservation(phone, purpose, reservationId);
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Reservation reservation) {
        mapper.releaseCooldown(reservation.phone(), reservation.purpose(), reservation.reservationId());
    }

    public record Reservation(String phone, String purpose, String reservationId) { }
}
