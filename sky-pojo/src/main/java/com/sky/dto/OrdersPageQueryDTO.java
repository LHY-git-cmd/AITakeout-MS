package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "订单分页查询数据传输对象")
public class OrdersPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码必须大于0")
    @Schema(description = "页码，从1开始", defaultValue = "1")
    private int page;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页记录数", defaultValue = "10")
    private int pageSize;

    @Schema(description = "订单号，用于模糊查询")
    private String number;

    @Schema(description = "手机号，用于查询")
    private String phone;

    @Schema(description = "订单状态")
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询起始时间，格式 yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "查询结束时间，格式 yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "用户ID，C端查询时用于过滤个人订单")
    private Long userId;

}