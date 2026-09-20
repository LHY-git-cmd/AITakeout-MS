package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent知识库文档实体类
 * <p>
 * 代表上传到某个知识库中的一个文档，包含其元数据、版本信息和索引状态。
 * </p>
 */
@Data
public class AgentKnowledgeDocument {

    private Long id;                // 主键ID
    private String documentId;      // 文档唯一标识符 (UUID)
    private String kbId;            // 所属知识库标识符
    private String fileName;        // 原始文件名
    private String fileType;        // 文件类型 (pdf, txt, md)
    private String fileUrl;         // 文件存储URL
    private String fileHash;        // 文件内容哈希值 (SHA256)
    private Integer version;        // 文档版本号
    private Integer activeVersion;  // 当前在线版本号
    private Integer status;         // 索引状态 (0:待索引, 1:索引中, 2:已完成, 3:失败)
    private Integer chunkCount;     // 文档分块数量
    private String errorMsg;        // 索引失败错误信息
    private Long createUser;        // 创建用户ID
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}