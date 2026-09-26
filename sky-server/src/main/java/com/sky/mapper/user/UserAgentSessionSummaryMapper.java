package com.sky.mapper.user;

import com.sky.entity.AgentSessionSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户端Agent摘要持久化边界，只允许访问user_agent_session_summary。 */
@Mapper
public interface UserAgentSessionSummaryMapper {
    AgentSessionSummary getBySessionId(String sessionId);
    int insert(AgentSessionSummary summary);
    int updateOptimistic(@Param("sessionId") String sessionId,
                         @Param("summary") String summary,
                         @Param("untilSeq") Integer untilSeq,
                         @Param("expectedVersion") Integer expectedVersion);
}
