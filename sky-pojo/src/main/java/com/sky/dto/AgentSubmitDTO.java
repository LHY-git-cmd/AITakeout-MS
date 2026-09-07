package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * Agent任务提交DTO
 * 用于submit + SSE模式的任务提交请求
 */
@Data
@Schema(description = "Agent任务提交请求参数")
public class AgentSubmitDTO implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "客户端预生成的任务ID，重试时必须复用")
    private String taskId;

    /**
     * 用户提问内容
     */
    @NotBlank(message = "提问内容不能为空")
    @Schema(description = "用户提问内容")
    private String query;

    /**
     * 会话ID（首轮为null，后续轮次由前端传回）
     */
    @Schema(description = "会话ID，首轮为空，后续轮次由前端传回")
    private String sessionId;

    /**
     * 知识库ID（可选，首次绑定后不可切换）
     */
    @Schema(description = "知识库ID，首次绑定后不可切换")
    private String kbId;

    /**
     * 使用的模型名称（可选）
     */
    @Schema(description = "使用的模型名称（可选）")
    private String model;
}
