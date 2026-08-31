package com.sky.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentSubmitResponse(
        @JsonProperty("task_id") String taskId,
        String status,
        @JsonProperty("stream_url") String streamUrl) {
}
