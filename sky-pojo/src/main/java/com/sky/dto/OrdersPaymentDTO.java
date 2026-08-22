package com.sky.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class OrdersPaymentDTO implements Serializable {
    //订单号
    @NotBlank(message = "订单号不能为空")
    private String orderNumber;

    //付款方式
    @Min(value = 1, message = "付款方式参数不正确")
    @Max(value = 2, message = "付款方式参数不正确")
    private Integer payMethod;

}
