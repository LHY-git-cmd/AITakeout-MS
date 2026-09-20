package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 不可变的账户账本分录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLedgerEntry {
    private Long id;                    // 唯一标识符
    private Long transferId;            // 关联的转账ID
    private Long accountId;             // 账户ID
    private String direction;           // 资金方向（DEBIT/CREDIT）
    private Long amountCent;            // 金额（分）
    private Long balanceAfterCent;      // 交易后余额（分）
    private LocalDateTime createTime;   // 创建时间
}