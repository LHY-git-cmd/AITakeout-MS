package com.sky.service;

import com.sky.dto.AgentSessionPageQueryDTO;
import com.sky.dto.AgentSessionUpdateDTO;
import com.sky.dto.AgentSubmitDTO;
import com.sky.dto.AgentTaskPageQueryDTO;
import com.sky.entity.AgentEvent;
import com.sky.result.PageResult;
import com.sky.vo.*;

import java.util.List;

/**
 * Agent智能体业务层接口
 * 提供会话管理、任务提交、消息查询等功能
 */
public interface AgentService {

    /**
     * 提交任务（submit + SSE模式）
     * 创建会话或复用已有会话，调用Python Agent submit接口，记录task
     *
     * @param dto 提交参数
     * @return 提交结果（包含taskId和sessionId）
     */
    AgentSubmitVO submitTask(AgentSubmitDTO dto);

    /**
     * 分页查询会话列表（当前用户）
     */
    PageResult pageQuerySessions(AgentSessionPageQueryDTO dto);

    /**
     * 查询会话详情（含消息历史）
     */
    AgentSessionDetailVO getSessionDetail(String sessionId);

    /**
     * 更新会话（修改标题、归档、删除）
     */
    void updateSession(AgentSessionUpdateDTO dto);

    /**
     * 分页查询任务列表
     */
    PageResult pageQueryTasks(AgentTaskPageQueryDTO dto);

    /**
     * 查询单个任务详情
     */
    AgentTaskVO getTaskDetail(String taskId);

    /**
     * 取消正在执行的任务
     */
    void cancelTask(String taskId);

    /**
     * 根据taskId查询该任务的SSE事件（断线恢复用）
     *
     * @param taskId    任务ID
     * @param lastSeqNo 上次收到的最后一个事件序号（0表示从头开始）
     * @return 事件列表
     */
    List<AgentEvent> getEventsAfterSeqNo(String taskId, Integer lastSeqNo);
}