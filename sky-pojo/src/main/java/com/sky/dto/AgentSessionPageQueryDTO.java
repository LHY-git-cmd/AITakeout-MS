package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "Agent会话分页查询数据传输对象")
public class AgentSessionPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码，从1开始", defaultValue = "1")
    private int page;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页记录数", defaultValue = "10")
    private int pageSize;

    @Size(max = 40, message = "知识库ID过长")
    @Schema(description = "知识库ID，用于筛选")
    private String kbId;

    @Min(value = 1, message = "无效的状态值")
    @Max(value = 3, message = "无效的状态值")
    @Schema(description = "会话状态 (1:进行中, 2:已归档, 3:已删除)")
    private Integer status;

    @Size(max = 100, message = "关键词过长")
    @Schema(description = "搜索关键词，将匹配会话标题")
    private String keyword;

}