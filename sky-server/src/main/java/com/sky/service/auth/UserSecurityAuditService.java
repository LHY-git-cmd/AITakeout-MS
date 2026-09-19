package com.sky.service.auth;

import com.sky.auth.AuthClientContext;
import com.sky.entity.UserSecurityAudit;
import com.sky.mapper.UserSecurityAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Commits authentication audit events independently of the request transaction. */
@Service
@RequiredArgsConstructor
public class UserSecurityAuditService {
    private final UserSecurityAuditMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String eventType, AuthClientContext client, String detail) {
        mapper.insert(UserSecurityAudit.builder().userId(userId).eventType(eventType).detailJson(detail)
                .ipAddress(client.ipAddress()).userAgent(client.userAgent()).createTime(LocalDateTime.now()).build());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public int countFailures(Long userId, LocalDateTime since) {
        return mapper.countLoginFailures(userId, since);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public UserSecurityAudit latest(Long userId, String eventType) {
        return mapper.findLatest(userId, eventType);
    }
}
