package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@Schema(description = "Python工具函数调用Java原子业务操作的请求数据传输对象")
public record AgentToolOperationRequest(
        @JsonProperty("request_id") @Schema(description = "请求ID，用于日志追踪") String requestId,
        @JsonProperty("task_id") @NotBlank @Schema(description = "当前任务的ID") String taskId,
        @JsonProperty("tool_call_id") @NotBlank @Schema(description = "工具调用ID") String toolCallId,
        @NotBlank @Schema(description = "要执行的操作名称") String operation,
        @NotNull @Schema(description = "操作所需的参数") JsonNode arguments
) implements Serializable {
}