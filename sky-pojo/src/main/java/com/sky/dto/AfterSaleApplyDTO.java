package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户取消或售后申请。 */
@Data
public class AfterSaleApplyDTO {
    @NotBlank(message = "申请原因不能为空")
    @Size(max = 255, message = "申请原因不能超过255个字符")
    private String reason;
}
