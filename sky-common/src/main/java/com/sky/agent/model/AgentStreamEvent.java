package com.sky.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record AgentStreamEvent(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("seq_no") Integer seqNo,
        String event,
        JsonNode data) {

    public AgentStreamEvent(String taskId, Integer seqNo, String event, JsonNode data) {
        this(taskId, null, seqNo, event, data);
    }
}
