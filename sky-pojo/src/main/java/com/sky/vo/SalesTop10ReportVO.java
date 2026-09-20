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
@Schema(description = "销量前10报告视图对象")
public class SalesTop10ReportVO implements Serializable {

    @Schema(description = "商品名称列表，以逗号分隔")
    private String nameList;

    @Schema(description = "销量列表，以逗号分隔")
    private String numberList;

}