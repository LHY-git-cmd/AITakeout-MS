package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单取消DTO
 * 用于管理端取消订单请求参数
 */
@Data
public class OrdersCancelDTO implements Serializable {

    // 订单ID
    private Long id;

    // 订单取消原因
    private String cancelReason;

}