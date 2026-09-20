package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "知识库工具视图对象")
public record KnowledgeBaseToolVO(
        @Schema(description = "知识库ID") String kbId,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "状态") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) implements Serializable {
}