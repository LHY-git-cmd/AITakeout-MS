package com.sky.vo;

import java.time.LocalDateTime;

/** 提供给AI工具的知识库文档字段白名单，不包含存储地址、文件哈希和内部错误。 */
public record KnowledgeDocumentToolVO(
        String documentId,
        String kbId,
        String fileName,
        String fileType,
        Integer version,
        Integer activeVersion,
        Integer status,
        Integer chunkCount,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
