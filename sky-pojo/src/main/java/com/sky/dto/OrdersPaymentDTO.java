package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
@Schema(description = "订单支付数据传输对象")
public class OrdersPaymentDTO implements Serializable {
    @NotBlank(message = "订单号不能为空")
    @Schema(description = "订单号")
    private String orderNumber;

    @Min(value = 1, message = "付款方式参数不正确")
    @Max(value = 2, message = "付款方式参数不正确")
    @Schema(description = "付款方式 (1:微信, 2:支付宝)")
    private Integer payMethod;

}