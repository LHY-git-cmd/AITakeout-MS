package com.sky.mapper;

import com.sky.entity.AgentToolConfirmation;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentToolConfirmationMapper {
    @Insert("""
            insert ignore into agent_tool_confirmation
                (confirmation_id, task_id, tool_call_id, employee_id, actor_role,
                 operation, arguments_json, argument_hash, resource_version, summary,
                 status, expires_at, create_time, update_time)
            values
                (#{confirmationId}, #{taskId}, #{toolCallId}, #{employeeId}, #{actorRole},
                 #{operation}, #{argumentsJson}, #{argumentHash}, #{resourceVersion}, #{summary},
                 #{status}, #{expiresAt}, now(), now())
            """)
    int insertIgnore(AgentToolConfirmation value);

    @Select("select * from agent_tool_confirmation where confirmation_id = #{id}")
    AgentToolConfirmation getByConfirmationId(String id);

    @Select("select * from agent_tool_confirmation where task_id = #{taskId} and tool_call_id = #{toolCallId}")
    AgentToolConfirmation getByTaskAndCall(@Param("taskId") String taskId,
                                           @Param("toolCallId") String toolCallId);

    @Update("""
            update agent_tool_confirmation
            set status = #{target},
                confirmed_at = case when #{target} = 'CONFIRMED' then now() else confirmed_at end,
                update_time = now()
            where confirmation_id = #{id} and employee_id = #{employeeId}
              and status = #{expected} and expires_at > now()
            """)
    int transitionByActor(@Param("id") String id, @Param("employeeId") Long employeeId,
                          @Param("expected") String expected, @Param("target") String target);

    @Update("""
            update agent_tool_confirmation set status = #{target}, update_time = now()
            where confirmation_id = #{id} and status = #{expected}
            """)
    int transition(@Param("id") String id, @Param("expected") String expected,
                   @Param("target") String target);

    @Update("""
            update agent_tool_confirmation
            set status = 'EXECUTED', executed_at = now(), update_time = now()
            where confirmation_id = #{id} and status = 'EXECUTING'
            """)
    int markExecuted(String id);
}
