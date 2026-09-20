package com.sky.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent消息引用实体
 */
@Data
public class AgentMessageCitation {

    private Long id;                    // 主键
    private String messageId;           // 消息ID
    private String kbId;                // 知识库ID
    private String documentId;          // 文档ID
    private Integer documentVersion;    // 文档版本
    private String chunkId;             // 块ID
    private String fileName;            // 文件名
    private Integer pageNo;             // 页码
    private BigDecimal score;           // 分数
    private String quote;               // 引用内容
    private LocalDateTime createTime;   // 创建时间
}