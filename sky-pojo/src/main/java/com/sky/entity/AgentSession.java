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
    private Long id;                    // 主键
    private String sessionId;           // 会话ID
    private Long userId;                // 用户ID
    private String kbId;                // 知识库ID
    private String title;               // 标题
    private Integer status;             // 状态 (1:进行中, 2:已归档, 3:已删除)
    private String lastTaskId;          // 最新任务ID
    private Integer messageCount;       // 消息数量
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
    private Long createUser;            // 创建人
    private Long updateUser;            // 更新人
}