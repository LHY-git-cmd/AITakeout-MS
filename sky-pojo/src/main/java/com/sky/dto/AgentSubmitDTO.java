package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "Agent任务提交数据传输对象")
public class AgentSubmitDTO implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "客户端预生成的任务ID，用于幂等控制，重试时必须复用")
    private String taskId;

    @NotBlank(message = "提问内容不能为空")
    @Schema(description = "用户提问内容")
    private String query;

    @Schema(description = "会话ID，首轮对话为空，后续对话需传回")
    private String sessionId;

    @Schema(description = "知识库ID，可选，首次绑定后不可切换")
    private String kbId;

    @Schema(description = "使用的模型名称，可选")
    private String model;
}