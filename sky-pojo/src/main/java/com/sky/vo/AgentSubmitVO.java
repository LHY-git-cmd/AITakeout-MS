package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent任务提交响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent任务提交响应数据")
public class AgentSubmitVO implements Serializable {

    /**
     * 执行任务ID（前端SSE订阅用）
     */
    @Schema(description = "执行任务ID，前端用于SSE订阅事件流")
    private String taskId;

    /**
     * 会话ID（后续轮次复用）
     */
    @Schema(description = "会话ID，后续轮次对话复用")
    private String sessionId;

    @Schema(description = "SSE事件订阅地址")
    private String eventsUrl;

    @Schema(description = "初始任务状态（0-排队）")
    private Integer status;

    private String assistantMessageId;
    private String errorMsg;
}
