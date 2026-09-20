package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 模拟资金账户。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockAccount {
    private Long id;                // 唯一标识符
    private String accountNo;       // 账户号
    private String accountType;     // 账户类型
    private Long ownerId;           // 所有者ID
    private Long availableCent;     // 可用余额（分）
    private Long frozenCent;        // 冻结金额（分）
    private Integer version;        // 版本号
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}