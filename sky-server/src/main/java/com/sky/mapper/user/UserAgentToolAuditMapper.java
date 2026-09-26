package com.sky.mapper.user;

import com.sky.entity.AgentToolAudit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** 用户工具审计只写入user_agent_tool_audit。 */
@Mapper
public interface UserAgentToolAuditMapper {
    @Insert("""
            insert ignore into user_agent_tool_audit
                (request_id, task_id, tool_call_id, user_id, operation, required_permission,
                 argument_hash, status, error_code, duration_ms, trace_id, create_time)
            values
                (#{requestId}, #{taskId}, #{toolCallId}, #{actorId}, #{operation}, #{requiredPermission},
                 #{argumentHash}, #{status}, #{errorCode}, #{durationMs}, #{traceId}, now())
            """)
    void insert(AgentToolAudit audit);
}
