package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent会话响应VO（列表展示用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent会话响应数据")
public class AgentSessionVO implements Serializable {

    @Schema(description = "主键值")
    private Long id;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "会话状态（1-进行中，2-已归档，3-已删除）")
    private Integer status;

    @Schema(description = "最后一个任务ID")
    private String lastTaskId;

    @Schema(description = "消息数量")
    private Integer messageCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}