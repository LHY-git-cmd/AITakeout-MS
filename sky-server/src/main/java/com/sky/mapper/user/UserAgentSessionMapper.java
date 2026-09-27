package com.sky.mapper.user;

import com.github.pagehelper.Page;
import com.sky.entity.AgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户端Agent会话持久化边界，只允许访问user_agent_session。 */
@Mapper
public interface UserAgentSessionMapper {
    void insert(AgentSession session);
    AgentSession getBySessionId(String sessionId);
    AgentSession getBySessionIdForUpdate(String sessionId);
    AgentSession getOwned(@Param("sessionId") String sessionId, @Param("userId") Long userId);
    Page<AgentSession> pageQuery(@Param("userId") Long userId, @Param("status") Integer status);
    void update(AgentSession session);
    int incrementMessageCount(@Param("id") Long id, @Param("lastTaskId") String lastTaskId);
}
