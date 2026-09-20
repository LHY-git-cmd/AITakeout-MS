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
    private Long id;
    private String accountNo;
    private String accountType;
    private Long ownerId;
    private Long availableCent;
    private Long frozenCent;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
