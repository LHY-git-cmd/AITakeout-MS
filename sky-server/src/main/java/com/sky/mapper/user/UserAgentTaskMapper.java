package com.sky.mapper.user;

import com.github.pagehelper.Page;
import com.sky.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户端Agent任务持久化边界，只允许访问user_agent_task。 */
@Mapper
public interface UserAgentTaskMapper {
    int insertIgnore(AgentTask task);
    AgentTask getByTaskId(String taskId);
    AgentTask getOwned(@Param("taskId") String taskId, @Param("userId") Long userId);
    List<AgentTask> listUnfinished();
    Page<AgentTask> pageQuery(@Param("sessionId") String sessionId,
                              @Param("userId") Long userId,
                              @Param("status") Integer status);
    int transitionStatus(@Param("taskId") String taskId,
                         @Param("status") Integer status,
                         @Param("progress") Integer progress,
                         @Param("assistantMessageId") String assistantMessageId,
                         @Param("errorMsg") String errorMsg,
                         @Param("expectedStatuses") List<Integer> expectedStatuses);
    void updateStatusAndProgress(@Param("taskId") String taskId,
                                 @Param("status") Integer status,
                                 @Param("progress") Integer progress,
                                 @Param("errorMsg") String errorMsg);
}
