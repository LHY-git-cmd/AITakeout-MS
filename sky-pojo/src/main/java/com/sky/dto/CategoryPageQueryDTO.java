package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.io.Serializable;

@Data
@Schema(description = "分类分页查询数据传输对象")
public class CategoryPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码必须大于0")
    @Schema(description = "页码，从1开始", defaultValue = "1")
    private int page;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    @Schema(description = "每页记录数", defaultValue = "10")
    private int pageSize;

    @Schema(description = "分类名称，用于模糊查询")
    private String name;

    @Schema(description = "分类类型 (1:菜品分类, 2:套餐分类)")
    private Integer type;

}