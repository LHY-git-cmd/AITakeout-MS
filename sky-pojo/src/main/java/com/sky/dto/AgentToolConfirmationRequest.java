package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

@Schema(description = "执行已确认工具操作的请求数据传输对象")
public record AgentToolConfirmationRequest(
        @JsonProperty("confirmation_id") @NotBlank @Schema(description = "确认ID，由服务端生成并返回给客户端") String confirmationId
) implements Serializable {
}