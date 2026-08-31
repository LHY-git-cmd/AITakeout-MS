package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.AgentMessage;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent智能体对话消息数据访问接口
 */
@Mapper
public interface AgentMessageMapper {

    /**
     * 新增消息
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(AgentMessage message);

    /** 同一任务同一角色只写入一次 */
    int insertIfAbsent(AgentMessage message);

    /**
     * 根据messageId查询
     */
    @Select("select * from agent_message where message_id = #{messageId}")
    AgentMessage getByMessageId(String messageId);

    /**
     * 查询指定会话的所有消息（按seq_no升序）
     */
    @Select("select * from agent_message where session_id = #{sessionId} order by seq_no asc")
    List<AgentMessage> listBySessionIdOrderBySeqNo(String sessionId);

    /** 查询会话最近的消息，并按对话顺序返回 */
    List<AgentMessage> listRecentBySessionId(@Param("sessionId") String sessionId,
                                             @Param("limit") Integer limit);

    /**
     * 查询指定任务关联的消息
     */
    @Select("select * from agent_message where task_id = #{taskId} order by seq_no asc")
    List<AgentMessage> listByTaskId(String taskId);

    /**
     * 统计指定会话的消息数
     */
    @Select("select count(id) from agent_message where session_id = #{sessionId}")
    int countBySessionId(String sessionId);

    /**
     * 查询指定会话的下一个seq_no
     */
    @Select("select coalesce(max(seq_no), 0) + 1 from agent_message where session_id = #{sessionId}")
    int getNextSeqNo(String sessionId);
}
