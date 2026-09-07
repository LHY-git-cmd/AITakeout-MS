package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "Agent回答引用")
public class AgentCitationVO implements Serializable {

    @Schema(description = "知识库ID")
    private String kbId;

    @Schema(description = "文档ID")
    private String documentId;

    @Schema(description = "文档版本")
    private Integer documentVersion;

    @Schema(description = "文档分块ID")
    private String chunkId;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "页码")
    private Integer pageNo;

    @Schema(description = "相关度分数")
    private BigDecimal score;

    @Schema(description = "引用原文")
    private String quote;
}
