package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 登录用户的搜索历史。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchHistory {
    private Long id;
    private Long userId;
    private String keyword;
    private String normalizedKeyword;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
