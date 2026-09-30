package com.sky.mapper.user;

import com.sky.entity.AgentToolConfirmation;
import org.apache.ibatis.annotations.*;

/** 用户确认凭证只访问user_agent_tool_confirmation。 */
@Mapper
public interface UserAgentToolConfirmationMapper {
    @Insert("""
            insert ignore into user_agent_tool_confirmation
                (confirmation_id, task_id, tool_call_id, user_id, operation, arguments_json,
                 argument_hash, resource_version, summary, status, expires_at, create_time, update_time)
            values
                (#{confirmationId}, #{taskId}, #{toolCallId}, #{actorId}, #{operation}, #{argumentsJson},
                 #{argumentHash}, #{resourceVersion}, #{summary}, #{status}, #{expiresAt}, now(), now())
            """)
    int insertIgnore(AgentToolConfirmation value);

    @Select("""
            select c.*, c.user_id as actor_id, 'USER' as actor_type, 'CUSTOMER' as actor_role
            from user_agent_tool_confirmation c where confirmation_id = #{id}
            """)
    AgentToolConfirmation getByConfirmationId(String id);

    @Select("""
            select c.*, c.user_id as actor_id, 'USER' as actor_type, 'CUSTOMER' as actor_role
            from user_agent_tool_confirmation c
            where task_id = #{taskId} and tool_call_id = #{toolCallId}
            """)
    AgentToolConfirmation getByTaskAndCall(@Param("taskId") String taskId,
                                           @Param("toolCallId") String toolCallId);

    @Update("""
            update user_agent_tool_confirmation
            set status = #{target},
                confirmed_at = case when #{target} = 'CONFIRMED' then now() else confirmed_at end,
                update_time = now()
            where confirmation_id = #{id} and user_id = #{userId}
              and status = #{expected} and expires_at > now()
            """)
    int transitionByUser(@Param("id") String id, @Param("userId") Long userId,
                         @Param("expected") String expected, @Param("target") String target);

    @Update("""
            update user_agent_tool_confirmation set status = #{target}, update_time = now()
            where confirmation_id = #{id} and status = #{expected}
            """)
    int transition(@Param("id") String id, @Param("expected") String expected,
                   @Param("target") String target);

    @Update("""
            update user_agent_tool_confirmation
            set status = 'EXECUTED', executed_at = now(), update_time = now()
            where confirmation_id = #{id} and status = 'EXECUTING'
            """)
    int markExecuted(@Param("id") String id);
}
