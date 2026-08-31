package com.sky.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AgentSubmitRequest(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("user_id") Long userId,
        String query,
        String model,
        Double temperature,
        Map<String, List<AgentHistoryMessage>> context) {
}
