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

    /**
     * 数据库主键ID
     */
    private Long id;

    /**
     * 文档的唯一业务标识符，通常由UUID生成
     */
    private String documentId;

    /**
     * 所属知识库的唯一标识符
     */
    private String kbId;

    /**
     * 文档的原始文件名
     */
    private String fileName;

    /**
     * 文档的文件类型（例如：pdf, txt, md）
     */
    private String fileType;

    /**
     * 文档的存储URL，指向对象存储或本地文件系统
     */
    private String fileUrl;

    /**
     * 最新版本文件的内容哈希值（例如：SHA256），用于检测文件变更
     */
    private String fileHash;

    /**
     * 文档的版本号，每次上传新版本时递增
     */
    private Integer version;

    /**
     * 当前在线生效的版本号
     */
    private Integer activeVersion;

    /**
     * 文档的索引状态
     * 0: 待索引
     * 1: 索引中
     * 2: 已完成
     * 3: 失败
     */
    private Integer status;

    /**
     * 文档被切分成的块（Chunk）的数量
     */
    private Integer chunkCount;

    /**
     * 如果索引失败，记录错误信息
     */
    private String errorMsg;

    /**
     * 创建该文档的用户ID
     */
    private Long createUser;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间
     */
    private LocalDateTime updateTime;
}