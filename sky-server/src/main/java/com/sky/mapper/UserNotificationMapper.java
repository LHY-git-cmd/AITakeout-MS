package com.sky.mapper;

import com.sky.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 用户通知持久化接口。 */
@Mapper
public interface UserNotificationMapper {
    int insert(UserNotification notification);
    UserNotification findByBusinessKey(String businessKey);
    List<UserNotification> list(@Param("userId") Long userId, @Param("beforeId") Long beforeId,
                                @Param("since") LocalDateTime since, @Param("limit") int limit);
    long countUnread(Long userId);
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
    int deleteBefore(LocalDateTime cutoff);
}
