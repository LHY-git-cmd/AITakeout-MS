package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "管理端售后审核请求数据传输对象")
public class AfterSaleReviewDTO implements Serializable {
    @NotNull(message = "审核结果不能为空")
    @Schema(description = "审核结果，true为通过，false为驳回")
    private Boolean approved;

    @Size(max = 255, message = "审核原因不能超过255个字符")
    @Schema(description = "审核原因，驳回时填写")
    private String reason;
}