package com.sky.mapper.user;

import com.sky.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户端Agent消息持久化边界，只允许访问user_agent_message。 */
@Mapper
public interface UserAgentMessageMapper {
    void insert(AgentMessage message);
    int insertIfAbsent(AgentMessage message);
    List<AgentMessage> listBySessionIdOrderBySeqNo(String sessionId);
    List<AgentMessage> listRecentBySessionId(@Param("sessionId") String sessionId,
                                             @Param("limit") Integer limit);
    int getNextSeqNo(String sessionId);
}
