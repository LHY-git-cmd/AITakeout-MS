package com.sky.mapper;

import com.sky.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserSessionMapper {
    int insert(UserSession session);
    UserSession findByRefreshTokenHash(String hash);
    int revoke(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);
    int revokeAll(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
    List<UserSession> findActiveByUserId(Long userId);
}
