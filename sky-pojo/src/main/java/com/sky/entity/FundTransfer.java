package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 一次完整的资金转移。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundTransfer {
    private Long id;                    // 唯一标识符
    private String transferNo;          // 转账单号
    private String businessKey;         // 业务键
    private String transferType;        // 转账类型
    private Long sourceAccountId;       // 源账户ID
    private Long targetAccountId;       // 目标账户ID
    private Long amountCent;            // 金额（分）
    private String status;              // 状态
    private Long operatorId;            // 操作员ID
    private String reason;              // 原因
    private Long adjustedAccountId;     // 调整账户ID
    private Long balanceBeforeCent;     // 交易前余额（分）
    private Long balanceAfterCent;      // 交易后余额（分）
    private LocalDateTime completedAt;  // 完成时间
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}