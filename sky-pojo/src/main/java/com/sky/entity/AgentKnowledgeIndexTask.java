package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent知识库文档索引任务实体类
 * <p>
 * 代表一个对特定文档版本进行索引的异步任务。
 * </p>
 */
@Data
public class AgentKnowledgeIndexTask {

    /**
     * 数据库主键ID
     */
    private Long id;

    /**
     * 任务的唯一业务标识符，通常由UUID生成
     */
    private String taskId;

    /**
     * 关联的文档ID
     */
    private String documentId;

    /**
     * 正在索引的文档版本号
     */
    private Integer documentVersion;

    /**
     * 任务状态
     * 0: 排队中
     * 1: 处理中
     * 2: 成功
     * 3: 失败
     */
    private Integer status;

    /**
     * 任务进度，百分比（0-100）
     */
    private Integer progress;

    /**
     * 索引请求的哈希值，用于幂等性检查
     */
    private String requestHash;

    /**
     * 如果任务失败，记录错误信息
     */
    private String errorMsg;

    /**
     * 任务开始处理的时间
     */
    private LocalDateTime startedAt;

    /**
     * 任务完成或失败的时间
     */
    private LocalDateTime finishedAt;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间
     */
    private LocalDateTime updateTime;
}