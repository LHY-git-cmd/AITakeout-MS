package com.sky.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单分页查询DTO
 * 用于管理端订单条件搜索的请求参数
 */
@Data
public class OrdersPageQueryDTO implements Serializable {

    // 页码
    @Min(value = 1, message = "页码必须大于0")
    private int page;

    // 每页条数
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private int pageSize;

    // 订单号
    private String number;

    // 手机号
    private String phone;

    // 订单状态
    private Integer status;

    // 开始时间
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    // 结束时间
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    // 用户ID（用于C端查询时过滤）
    private Long userId;

}
