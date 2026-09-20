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

    private Long id;                    // 主键ID
    private String taskId;              // 任务唯一标识符 (UUID)
    private String documentId;          // 关联的文档ID
    private Integer documentVersion;    // 正在索引的文档版本号
    private Integer status;             // 任务状态 (0:排队, 1:处理中, 2:成功, 3:失败)
    private Integer progress;           // 任务进度 (0-100)
    private String requestHash;         // 请求哈希值 (用于幂等)
    private String errorMsg;            // 任务失败错误信息
    private LocalDateTime startedAt;    // 任务开始时间
    private LocalDateTime finishedAt;   // 任务结束时间
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}