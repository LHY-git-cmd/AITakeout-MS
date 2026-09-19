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
    private Long id;
    private String transferNo;
    private String businessKey;
    private String transferType;
    private Long sourceAccountId;
    private Long targetAccountId;
    private Long amountCent;
    private String status;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
