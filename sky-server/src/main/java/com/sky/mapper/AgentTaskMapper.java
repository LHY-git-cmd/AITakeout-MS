package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.entity.AgentTask;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent智能体执行任务数据访问接口
 */
@Mapper
public interface AgentTaskMapper {

    /**
     * 新增任务
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(AgentTask task);

    /**
     * 根据DB主键查询
     */
    @Select("select * from agent_task where id = #{id}")
    AgentTask getById(Long id);

    /**
     * 根据业务taskId查询
     */
    @Select("select * from agent_task where task_id = #{taskId}")
    AgentTask getByTaskId(String taskId);

    @Select("select * from agent_task where task_id = #{taskId} and user_id = #{userId}")
    AgentTask getByTaskIdAndUserId(@Param("taskId") String taskId,
                                   @Param("userId") Long userId);

    /**
     * 更新任务信息
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(AgentTask task);

    /**
     * 更新任务状态和进度
     */
    @AutoFill(value = OperationType.UPDATE)
    void updateStatusAndProgress(@Param("taskId") String taskId,
                                @Param("status") Integer status,
                                @Param("progress") Integer progress,
                                @Param("errorMsg") String errorMsg);

    int transitionStatus(@Param("taskId") String taskId,
                         @Param("status") Integer status,
                         @Param("progress") Integer progress,
                         @Param("assistantMessageId") String assistantMessageId,
                         @Param("errorMsg") String errorMsg,
                         @Param("expectedStatuses") List<Integer> expectedStatuses);

    @Select("select * from agent_task where status in (0, 1) order by create_time asc")
    List<AgentTask> listUnfinished();

    /**
     * 分页查询任务列表
     */
    Page<AgentTask> pageQuery(@Param("sessionId") String sessionId,
                              @Param("userId") Long userId,
                              @Param("status") Integer status);

    /**
     * 查询指定会话下的所有任务
     */
    @Select("select * from agent_task where session_id = #{sessionId} order by create_time asc")
    List<AgentTask> listBySessionId(String sessionId);
}
