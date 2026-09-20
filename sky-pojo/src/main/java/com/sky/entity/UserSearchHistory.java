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
    private Long id;                        // 唯一标识符
    private Long userId;                    // 用户ID
    private String keyword;                 // 搜索关键词
    private String normalizedKeyword;       // 规范化的搜索关键词
    private LocalDateTime createTime;       // 创建时间
    private LocalDateTime updateTime;       // 更新时间
}