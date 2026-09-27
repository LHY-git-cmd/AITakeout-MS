package com.sky.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户端创建空Agent会话的可选参数。 */
@Data
public class UserAgentSessionCreateDTO {
    @Size(max = 100, message = "会话标题过长")
    private String title;
}
