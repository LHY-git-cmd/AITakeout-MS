package com.sky.vo;

import java.time.LocalDateTime;

/** 提供给AI工具的知识库字段白名单。 */
public record KnowledgeBaseToolVO(
        String kbId,
        String name,
        String description,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
