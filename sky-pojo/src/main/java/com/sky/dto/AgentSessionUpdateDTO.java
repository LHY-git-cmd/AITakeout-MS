package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "Agent会话更新数据传输对象")
public class AgentSessionUpdateDTO implements Serializable {

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "会话新标题")
    private String title;

    @Schema(description = "会话状态 (2:已归档, 3:已删除)")
    private Integer status;
}