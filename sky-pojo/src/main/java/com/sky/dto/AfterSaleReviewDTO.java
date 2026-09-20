package com.sky.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理端售后审核请求。 */
@Data
public class AfterSaleReviewDTO {
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    @Size(max = 255, message = "审核原因不能超过255个字符")
    private String reason;
}
