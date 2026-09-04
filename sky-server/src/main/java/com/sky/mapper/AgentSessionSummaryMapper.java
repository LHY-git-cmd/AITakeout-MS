package com.sky.mapper;

import com.sky.entity.AgentSessionSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentSessionSummaryMapper {
    @Select("select * from agent_session_summary where session_id = #{sessionId}")
    AgentSessionSummary getBySessionId(String sessionId);

    int insert(AgentSessionSummary summary);

    int updateOptimistic(@Param("sessionId") String sessionId,
                         @Param("summary") String text,
                         @Param("untilSeq") Integer untilSeq,
                         @Param("expectedVersion") Integer expectedVersion);
}
