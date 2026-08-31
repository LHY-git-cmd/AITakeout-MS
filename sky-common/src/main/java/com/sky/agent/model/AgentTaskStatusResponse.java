package com.sky.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentTaskStatusResponse(
        @JsonProperty("task_id") String taskId,
        String status,
        String result,
        @JsonProperty("error_msg") String errorMsg) {
}
