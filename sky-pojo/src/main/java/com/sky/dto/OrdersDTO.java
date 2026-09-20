package com.sky.dto;

import com.sky.entity.OrderDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "订单数据传输对象")
public class OrdersDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "订单号")
    private String number;

    @Schema(description = "订单状态 (1:待付款, 2:待派送, 3:已派送, 4:已完成, 5:已取消)")
    private Integer status;

    @Schema(description = "下单用户ID")
    private Long userId;

    @Schema(description = "地址ID")
    private Long addressBookId;

    @Schema(description = "下单时间")
    private LocalDateTime orderTime;

    @Schema(description = "结账时间")
    private LocalDateTime checkoutTime;

    @Schema(description = "支付方式 (1:微信, 2:支付宝)")
    private Integer payMethod;

    @Schema(description = "实收金额")
    private BigDecimal amount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "收货人")
    private String consignee;

    @Schema(description = "订单详情列表")
    private List<OrderDetail> orderDetails;

}