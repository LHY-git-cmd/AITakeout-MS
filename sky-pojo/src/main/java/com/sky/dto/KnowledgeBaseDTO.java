package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseDTO {

    @NotBlank
    private String name;

    private String description;
    private String embeddingModel;
    /**
     * 文本分块策略 (Chunking Strategy)。
     * 定义了在将文档入库前如何将其切割成小块（chunks）。
     * 例如："sentence"（按句子切分）、"paragraph"（按段落切分）或自定义策略。
     */
    private String chunkStrategy;
    private Integer status;
}
