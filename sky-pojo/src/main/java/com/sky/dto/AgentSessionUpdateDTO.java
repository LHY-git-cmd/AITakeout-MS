package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * Agent会话更新DTO（修改标题、归档等）
 */
@Data
@Schema(description = "Agent会话更新请求参数")
public class AgentSessionUpdateDTO implements Serializable {

    /**
     * 会话ID（业务标识）
     */
    @Schema(description = "会话ID")
    private String sessionId;

    /**
     * 新标题
     */
    @Schema(description = "会话标题")
    private String title;

    /**
     * 状态（2归档 3删除）
     */
    @Schema(description = "会话状态（2-归档，3-删除）")
    private Integer status;
}