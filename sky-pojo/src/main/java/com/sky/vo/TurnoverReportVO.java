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
@Schema(description = "营业额报告视图对象")
public class TurnoverReportVO implements Serializable {

    @Schema(description = "日期列表，以逗号分隔")
    private String dateList;

    @Schema(description = "营业额列表，以逗号分隔")
    private String turnoverList;

}