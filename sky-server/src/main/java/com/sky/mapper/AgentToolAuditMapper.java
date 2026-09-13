package com.sky.mapper;

import com.sky.entity.AgentToolAudit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentToolAuditMapper {
    @Insert("""
            insert ignore into agent_tool_audit
                (request_id, task_id, tool_call_id, employee_id, actor_role, operation,
                 required_permission, argument_hash, status, error_code, duration_ms,
                 trace_id, create_time)
            values
                (#{requestId}, #{taskId}, #{toolCallId}, #{employeeId}, #{actorRole}, #{operation},
                 #{requiredPermission}, #{argumentHash}, #{status}, #{errorCode}, #{durationMs},
                 #{traceId}, now())
            """)
    void insert(AgentToolAudit audit);
}
