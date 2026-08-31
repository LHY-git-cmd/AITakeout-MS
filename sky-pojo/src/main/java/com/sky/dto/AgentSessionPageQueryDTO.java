package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * Agent会话分页查询DTO
 */
@Data
@Schema(description = "Agent会话分页查询请求参数")
public class AgentSessionPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码必须大于0")
    @Schema(description = "页码，默认1")
    private int page;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页条数，默认10")
    private int pageSize;

    /**
     * 会话状态筛选（1进行中 2已归档 3已删除）
     */
    @Schema(description = "会话状态（1-进行中，2-已归档，3-已删除）")
    private Integer status;
}