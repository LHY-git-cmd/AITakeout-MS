package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Python工具函数调用Java原子业务操作的请求。 */
public record AgentToolOperationRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("task_id") @NotBlank String taskId,
        @JsonProperty("tool_call_id") @NotBlank String toolCallId,
        @NotBlank String operation,
        @NotNull JsonNode arguments) {
}
