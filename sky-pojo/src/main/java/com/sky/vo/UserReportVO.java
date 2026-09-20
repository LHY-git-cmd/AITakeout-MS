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
@Schema(description = "用户报告视图对象")
public class UserReportVO implements Serializable {

    @Schema(description = "日期列表，以逗号分隔")
    private String dateList;

    @Schema(description = "用户总量列表，以逗号分隔")
    private String totalUserList;

    @Schema(description = "新增用户列表，以逗号分隔")
    private String newUserList;

}