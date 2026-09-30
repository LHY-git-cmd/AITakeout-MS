package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "知识库数据传输对象")
public class KnowledgeBaseDTO implements Serializable {

    @NotBlank(message = "知识库名称不能为空")
    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "使用的Embedding模型名称")
    private String embeddingModel;

    @Schema(description = "文本分块策略，定义文档如何被切割成小块 (例如: 'sentence', 'paragraph')")
    private String chunkStrategy;

    @Schema(description = "状态 (0:禁用, 1:启用)", defaultValue = "1")
    private Integer status;

    @Schema(description = "公共知识分类；内部知识库可不传", defaultValue = "GENERAL")
    private String category;
}
