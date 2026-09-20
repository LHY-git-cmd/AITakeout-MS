package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "订单提交数据传输对象")
public class OrdersSubmitDTO implements Serializable {
    @Schema(description = "服务端试算签名凭证")
    private String previewToken;

    @Schema(description = "配送模式 (IMMEDIATE:立即送达, SCHEDULED:预约送达)", defaultValue = "IMMEDIATE")
    private String deliveryMode = "IMMEDIATE";

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "预约送达时间的起始点")
    private LocalDateTime deliverySlotStart;

    @NotNull(message = "收货地址不能为空")
    @Schema(description = "地址簿ID")
    private Long addressBookId;

    @Min(value = 1, message = "付款方式参数不正确")
    @Max(value = 2, message = "付款方式参数不正确")
    @Schema(description = "付款方式 (1:微信, 2:支付宝)")
    private int payMethod;

    @Size(max = 100, message = "订单备注长度不能超过100个字符")
    @Schema(description = "备注")
    private String remark;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "预计送达时间")
    private LocalDateTime estimatedDeliveryTime;

    @NotNull(message = "配送状态不能为空")
    @Min(value = 0, message = "配送状态参数不正确")
    @Max(value = 1, message = "配送状态参数不正确")
    @Schema(description = "配送状态 (1:立即送出, 0:选择具体时间)")
    private Integer deliveryStatus;

    @Min(value = 0, message = "餐具数量不能小于0")
    @Max(value = 99, message = "餐具数量不能超过99")
    @Schema(description = "餐具数量")
    private Integer tablewareNumber;

    @NotNull(message = "餐具数量状态不能为空")
    @Min(value = 0, message = "餐具数量状态参数不正确")
    @Max(value = 1, message = "餐具数量状态参数不正确")
    @Schema(description = "餐具数量状态 (1:按餐量提供, 0:选择具体数量)")
    private Integer tablewareStatus;

    @Schema(description = "打包费")
    private Integer packAmount;

    @Schema(description = "总金额")
    private BigDecimal amount;
}