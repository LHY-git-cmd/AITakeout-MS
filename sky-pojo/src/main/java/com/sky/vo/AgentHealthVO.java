package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 管理端可展示的 Agent 服务健康状态，不包含内部地址或诊断详情。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent 服务健康状态")
public class AgentHealthVO implements Serializable {

    @Schema(description = "服务名称")
    private String service;

    @Schema(description = "服务状态：online、degraded 或 offline")
    private String status;

    @Schema(description = "状态检查时间，ISO-8601 UTC")
    private String checkedAt;

    @Schema(description = "脱敏错误类型")
    private String errorType;
}
