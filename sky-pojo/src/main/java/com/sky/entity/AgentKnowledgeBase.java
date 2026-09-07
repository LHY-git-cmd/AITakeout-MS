package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent知识库实体类
 * <p>
 * 代表一个独立的知识库，包含其元数据、配置和状态信息。
 * </p>
 */
@Data
public class AgentKnowledgeBase {

    /**
     * 数据库主键ID
     */
    private Long id;

    /**
     * 知识库的唯一业务标识符，通常由UUID生成
     */
    private String kbId;

    /**
     * 知识库的名称
     */
    private String name;

    /**
     * 知识库的详细描述
     */
    private String description;

    /**
     * 用于生成向量嵌入（Embedding）的模型名称
     */
    private String embeddingModel;

    /**
     * 文档分块（Chunking）策略的标识符
     */
    private String chunkStrategy;

    /**
     * 知识库状态
     * 0: 初始化中
     * 1: 可用
     * 2: 索引中
     * 3: 失败
     */
    private Integer status;

    /**
     * 创建该知识库的用户ID
     */
    private Long createUser;

    /**
     * 最后更新该知识库的用户ID
     */
    private Long updateUser;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间
     */
    private LocalDateTime updateTime;
}