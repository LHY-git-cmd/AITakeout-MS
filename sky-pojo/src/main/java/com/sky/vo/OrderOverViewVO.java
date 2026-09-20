package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单概览视图对象")
public class OrderOverViewVO implements Serializable {
    @Schema(description = "待接单数量")
    private Integer waitingOrders;

    @Schema(description = "待派送数量")
    private Integer deliveredOrders;

    @Schema(description = "已完成数量")
    private Integer completedOrders;

    @Schema(description = "已取消数量")
    private Integer cancelledOrders;

    @Schema(description = "全部订单数量")
    private Integer allOrders;
}