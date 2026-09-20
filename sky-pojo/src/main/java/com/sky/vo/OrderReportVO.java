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
@Schema(description = "订单报告视图对象")
public class OrderReportVO implements Serializable {

    @Schema(description = "日期列表，以逗号分隔")
    private String dateList;

    @Schema(description = "每日订单数列表，以逗号分隔")
    private String orderCountList;

    @Schema(description = "每日有效订单数列表，以逗号分隔")
    private String validOrderCountList;

    @Schema(description = "订单总数")
    private Integer totalOrderCount;

    @Schema(description = "有效订单数")
    private Integer validOrderCount;

    @Schema(description = "订单完成率")
    private Double orderCompletionRate;

}