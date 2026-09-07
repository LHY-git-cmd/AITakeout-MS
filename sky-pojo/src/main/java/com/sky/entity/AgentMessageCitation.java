package com.sky.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent消息引用实体
 */
@Data
public class AgentMessageCitation {

    /**
     * 主键
     */
    private Long id;
    /**
     * 消息ID
     */
    private String messageId;
    /**
     * 知识库ID
     */
    private String kbId;
    /**
     * 文档ID
     */
    private String documentId;
    /**
     * 文档版本
     */
    private Integer documentVersion;
    /**
     * 块ID
     */
    private String chunkId;
    /**
     * 文件名
     */
    private String fileName;
    /**
     * 页码
     */
    private Integer pageNo;
    /**
     * 分数
     */
    private BigDecimal score;
    /**
     * 引用内容
     */
    private String quote;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}