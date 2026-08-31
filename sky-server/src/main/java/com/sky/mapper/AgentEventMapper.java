package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.AgentEvent;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent智能体SSE事件数据访问接口
 */
@Mapper
public interface AgentEventMapper {

    /**
     * 新增单个事件
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(AgentEvent event);

    /** 幂等写入事件，重复taskId+seqNo时返回0 */
    int insertIfAbsent(AgentEvent event);

    /**
     * 批量新增事件
     */
    void batchInsert(@Param("list") List<AgentEvent> events);

    /**
     * 查询指定taskId的所有事件
     */
    @Select("select * from agent_event where task_id = #{taskId} order by seq_no asc")
    List<AgentEvent> listByTaskId(String taskId);

    /**
     * 断线恢复：查询seq_no大于lastSeqNo的事件
     */
    @Select("select * from agent_event where task_id = #{taskId} and seq_no > #{lastSeqNo} order by seq_no asc")
    List<AgentEvent> listByTaskIdAfterSeqNo(@Param("taskId") String taskId,
                                            @Param("lastSeqNo") Integer lastSeqNo);

    /**
     * 统计指定taskId的事件总数
     */
    @Select("select count(id) from agent_event where task_id = #{taskId}")
    int countByTaskId(String taskId);

    /**
     * 查询指定taskId的下一个seq_no
     */
    @Select("select coalesce(max(seq_no), 0) + 1 from agent_event where task_id = #{taskId}")
    int getNextSeqNo(String taskId);

    @Select("select coalesce(max(seq_no), 0) from agent_event where task_id = #{taskId}")
    int getMaxSeqNo(String taskId);
}
