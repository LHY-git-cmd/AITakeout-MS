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
@Schema(description = "Agent任务提交响应视图对象")
public class AgentSubmitVO implements Serializable {

    @Schema(description = "执行任务ID，前端用于SSE订阅事件流")
    private String taskId;

    @Schema(description = "会话ID，后续轮次对话复用")
    private String sessionId;

    @Schema(description = "SSE事件订阅地址")
    private String eventsUrl;

    @Schema(description = "初始任务状态（0-排队）")
    private Integer status;

    @Schema(description = "助手消息ID")
    private String assistantMessageId;

    @Schema(description = "错误信息")
    private String errorMsg;
}