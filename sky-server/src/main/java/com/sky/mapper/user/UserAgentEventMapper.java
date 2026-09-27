package com.sky.mapper.user;

import com.sky.entity.AgentEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户端Agent事件持久化边界，只允许访问user_agent_event。 */
@Mapper
public interface UserAgentEventMapper {
    int insertIfAbsent(AgentEvent event);
    List<AgentEvent> listByTaskIdAfterSeqNo(@Param("taskId") String taskId,
                                            @Param("lastSeqNo") Integer lastSeqNo);
    int getMaxSeqNo(String taskId);
}
