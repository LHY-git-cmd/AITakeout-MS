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

    private Long id;                    // 主键ID
    private String kbId;                // 知识库唯一标识符 (UUID)
    private String name;                // 知识库名称
    private String description;         // 知识库描述
    private String embeddingModel;      // Embedding模型名称
    private String chunkStrategy;       // 文档分块策略标识符
    private Integer status;             // 状态 (0:初始化, 1:可用, 2:索引中, 3:失败)
    private Long createUser;            // 创建用户ID
    private Long updateUser;            // 更新用户ID
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}