package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单接单DTO
 * 用于管理端接单请求参数
 */
@Data
public class OrdersConfirmDTO implements Serializable {

    // 订单ID
    private Long id;

    // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款
    private Integer status;

}