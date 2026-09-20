package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户取消或售后申请数据传输对象")
public class AfterSaleApplyDTO implements Serializable {
    @NotBlank(message = "申请原因不能为空")
    @Size(max = 255, message = "申请原因不能超过255个字符")
    @Schema(description = "申请原因")
    private String reason;
}