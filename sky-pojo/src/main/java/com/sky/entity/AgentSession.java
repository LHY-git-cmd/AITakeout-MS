package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSession implements Serializable {
    /**
     * 主键
     */
    private Long id;
    /**
     * 会话ID
     */
    private String sessionId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 知识库ID
     */
    private String kbId;
    /**
     * 标题
     */
    private String title;
    /**
     * 状态（1-进行中，2-已归档，3-已删除）
     */
    private Integer status;
    /**
     * 最新任务ID
     */
    private String lastTaskId;
    /**
     * 消息数量
     */
    private Integer messageCount;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    /**
     * 创建人
     */
    private Long createUser;
    /**
     * 更新人
     */
    private Long updateUser;
}