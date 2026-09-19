package com.sky.mapper;

import com.sky.entity.UserSecurityAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface UserSecurityAuditMapper {
    int insert(UserSecurityAudit audit);
    int countLoginFailures(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    UserSecurityAudit findLatest(@Param("userId") Long userId, @Param("eventType") String eventType);
}
