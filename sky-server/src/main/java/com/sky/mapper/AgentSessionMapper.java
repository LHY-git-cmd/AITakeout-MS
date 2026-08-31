package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.entity.AgentSession;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Agent智能体会话数据访问接口
 */
@Mapper
public interface AgentSessionMapper {

    /**
     * 新增会话
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(AgentSession session);

    /**
     * 根据DB主键查询
     */
    @Select("select * from agent_session where id = #{id}")
    AgentSession getById(Long id);

    /**
     * 根据业务sessionId查询
     */
    @Select("select * from agent_session where session_id = #{sessionId}")
    AgentSession getBySessionId(String sessionId);

    @Select("select * from agent_session where session_id = #{sessionId} for update")
    AgentSession getBySessionIdForUpdate(String sessionId);

    /**
     * 分页查询会话列表
     */
    Page<AgentSession> pageQuery(@Param("userId") Long userId,
                                 @Param("status") Integer status);

    /**
     * 更新会话信息
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(AgentSession session);

    int incrementMessageCount(@Param("id") Long id,
                              @Param("lastTaskId") String lastTaskId);

    /**
     * 软删除会话
     */
    @AutoFill(value = OperationType.UPDATE)
    void softDelete(@Param("id") Long id);
}
