package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@Data
@Schema(description = "订单接单数据传输对象")
public class OrdersConfirmDTO implements Serializable {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单状态 (1:待付款, 2:待接单, 3:已接单, 4:派送中, 5:已完成, 6:已取消, 7:退款)")
    private Integer status;

}