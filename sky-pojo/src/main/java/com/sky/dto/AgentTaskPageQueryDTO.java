package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * Agent任务分页查询DTO
 */
@Data
@Schema(description = "Agent任务分页查询请求参数")
public class AgentTaskPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码必须大于0")
    @Schema(description = "页码，默认1")
    private int page;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页条数，默认10")
    private int pageSize;

    /**
     * 会话ID（按会话筛选其下所有任务）
     */
    @Schema(description = "会话ID，按会话筛选其下所有任务")
    private String sessionId;

    /**
     * 任务状态（0排队 1执行中 2完成 3失败 4取消）
     */
    @Schema(description = "任务状态（0-排队，1-执行中，2-完成，3-失败，4-取消）")
    private Integer status;
}