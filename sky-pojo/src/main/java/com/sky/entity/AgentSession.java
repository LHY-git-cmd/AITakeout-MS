package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// AgentSession.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSession implements Serializable {
    private Long id;
    private String sessionId;
    private Long userId;
    private String title;
    private Integer status;       // 1进行中 2已归档 3已删除
    private String lastTaskId;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createUser;
    private Long updateUser;
}