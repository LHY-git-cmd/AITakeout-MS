package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent任务响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent任务响应数据")
public class AgentTaskVO implements Serializable {

    @Schema(description = "主键值")
    private Long id;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "用户提问内容")
    private String query;

    @Schema(description = "任务状态（0-排队，1-执行中，2-完成，3-失败，4-取消）")
    private Integer status;

    @Schema(description = "执行进度（0-100）")
    private Integer progress;

    @Schema(description = "使用的模型名称")
    private String model;

    @Schema(description = "助手消息ID")
    private String assistantMessageId;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}