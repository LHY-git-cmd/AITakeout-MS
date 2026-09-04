package com.sky.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AgentSessionSummary implements Serializable {
    private Long id;
    private String sessionId;
    private String summary;
    private Integer summaryUntilSeq;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
