package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单拒单DTO
 * 用于管理端拒单请求参数
 */
@Data
public class OrdersRejectionDTO implements Serializable {

    // 订单ID
    private Long id;

    // 订单拒绝原因
    private String rejectionReason;

}