package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/** 用户端Agent消息请求，不包含任何可伪造的主体或模型字段。 */
@Data
@Schema(description = "用户端Agent消息请求")
public class UserAgentSubmitDTO implements Serializable {
    @Size(max = 64, message = "会话ID过长")
    private String sessionId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息不能超过4000字")
    private String message;

    @Schema(description = "当前页面提示信息，不参与鉴权")
    private Map<String, Object> clientContext;
}
