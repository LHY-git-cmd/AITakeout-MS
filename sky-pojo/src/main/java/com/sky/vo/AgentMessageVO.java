package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent消息响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent消息响应数据")
public class AgentMessageVO implements Serializable {

    @Schema(description = "主键值")
    private Long id;

    @Schema(description = "消息ID")
    private String messageId;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "消息角色（1-用户，2-助手，3-系统）")
    private Integer role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "内容类型（text/tool_calls等）")
    private String contentType;

    @Schema(description = "Token数量")
    private Integer tokenCount;

    @Schema(description = "序号（会话内有序）")
    private Integer seqNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}