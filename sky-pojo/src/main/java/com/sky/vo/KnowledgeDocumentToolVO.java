package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "知识库文档工具视图对象")
public record KnowledgeDocumentToolVO(
        @Schema(description = "文档ID") String documentId,
        @Schema(description = "知识库ID") String kbId,
        @Schema(description = "文件名") String fileName,
        @Schema(description = "文件类型") String fileType,
        @Schema(description = "版本") Integer version,
        @Schema(description = "激活版本") Integer activeVersion,
        @Schema(description = "状态") Integer status,
        @Schema(description = "分块数量") Integer chunkCount,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) implements Serializable {
}