package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Java原子业务操作的统一响应信封。 */
public record AgentToolOperationResponse(
        @JsonProperty("tool_call_id") String toolCallId,
        String status,
        Object data,
        Map<String, Object> error,
        @JsonProperty("trace_id") String traceId) {

    public static AgentToolOperationResponse success(String callId, Object data, String traceId) {
        return new AgentToolOperationResponse(callId, "success", data, null, traceId);
    }

    public static AgentToolOperationResponse error(String callId, String status, String code,
                                                    String message, String traceId) {
        return new AgentToolOperationResponse(callId, status, null,
                Map.of("code", code, "message", message), traceId);
    }
}
