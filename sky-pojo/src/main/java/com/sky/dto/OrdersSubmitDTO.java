package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrdersSubmitDTO implements Serializable {
    //地址簿id
    @NotNull(message = "收货地址不能为空")
    private Long addressBookId;
    //付款方式
    @Min(value = 1, message = "付款方式参数不正确")
    @Max(value = 2, message = "付款方式参数不正确")
    private int payMethod;
    //备注
    @Size(max = 100, message = "订单备注长度不能超过100个字符")
    private String remark;
    //预计送达时间
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;
    //配送状态  1立即送出  0选择具体时间
    @NotNull(message = "配送状态不能为空")
    @Min(value = 0, message = "配送状态参数不正确")
    @Max(value = 1, message = "配送状态参数不正确")
    private Integer deliveryStatus;
    //餐具数量
    @Min(value = 0, message = "餐具数量不能小于0")
    @Max(value = 99, message = "餐具数量不能超过99")
    private Integer tablewareNumber;
    //餐具数量状态  1按餐量提供  0选择具体数量
    @NotNull(message = "餐具数量状态不能为空")
    @Min(value = 0, message = "餐具数量状态参数不正确")
    @Max(value = 1, message = "餐具数量状态参数不正确")
    private Integer tablewareStatus;
    //打包费
    private Integer packAmount;
    //总金额
    private BigDecimal amount;
}
