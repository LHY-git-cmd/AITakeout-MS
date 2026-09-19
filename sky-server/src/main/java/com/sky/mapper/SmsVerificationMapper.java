package com.sky.mapper;

import com.sky.entity.SmsVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface SmsVerificationMapper {
    int insert(SmsVerification verification);
    SmsVerification findLatest(@Param("phone") String phone, @Param("purpose") String purpose);
    int markUsed(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);
    int insertCooldown(@Param("phone") String phone, @Param("purpose") String purpose,
                       @Param("nextAllowedAt") LocalDateTime nextAllowedAt);
    int advanceCooldown(@Param("phone") String phone, @Param("purpose") String purpose,
                        @Param("now") LocalDateTime now, @Param("nextAllowedAt") LocalDateTime nextAllowedAt);
}
