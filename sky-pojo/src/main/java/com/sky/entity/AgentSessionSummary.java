package com.sky.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AgentSessionSummary implements Serializable {
    private Long id;                    // 唯一标识符
    private String sessionId;           // 会话ID
    private String summary;             // 会话摘要
    private Integer summaryUntilSeq;    // 摘要截止的消息序列号
    private Integer version;            // 版本号
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间
}