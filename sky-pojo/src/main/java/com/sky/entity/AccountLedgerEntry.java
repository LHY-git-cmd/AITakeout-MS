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
    private Long id;
    private Long transferId;
    private Long accountId;
    private String direction;
    private Long amountCent;
    private Long balanceAfterCent;
    private LocalDateTime createTime;
}
