package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** 执行已确认工具操作的请求。 */
public record AgentToolConfirmationRequest(
        @JsonProperty("confirmation_id") @NotBlank String confirmationId) {
}
