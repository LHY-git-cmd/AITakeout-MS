package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Map;

@Schema(description = "Agent工具操作响应视图对象")
public record AgentToolOperationResponse(
        @Schema(description = "工具调用ID") @JsonProperty("tool_call_id") String toolCallId,
        @Schema(description = "状态") String status,
        @Schema(description = "数据") Object data,
        @Schema(description = "错误信息") Map<String, Object> error,
        @Schema(description = "追踪ID") @JsonProperty("trace_id") String traceId) implements Serializable {

    public static AgentToolOperationResponse success(String callId, Object data, String traceId) {
        return new AgentToolOperationResponse(callId, "success", data, null, traceId);
    }

    public static AgentToolOperationResponse error(String callId, String status, String code,
                                                    String message, String traceId) {
        return new AgentToolOperationResponse(callId, status, null,
                Map.of("code", code, "message", message), traceId);
    }
}