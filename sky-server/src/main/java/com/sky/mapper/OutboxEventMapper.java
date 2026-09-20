package com.sky.mapper;

import com.sky.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 通知 Outbox 持久化接口。 */
@Mapper
public interface OutboxEventMapper {
    int insert(OutboxEvent event);
    List<OutboxEvent> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int markPublished(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt,
                      @Param("updateTime") LocalDateTime updateTime);
    int markFailed(@Param("id") Long id, @Param("expectedAttempts") int expectedAttempts,
                   @Param("status") String status, @Param("attemptCount") int attemptCount,
                   @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("lastError") String lastError,
                   @Param("updateTime") LocalDateTime updateTime);
}
