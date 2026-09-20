package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
@Schema(description = "分类数据传输对象")
public class CategoryDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "分类类型不能为空")
    @Min(value = 1, message = "分类类型参数不正确")
    @Max(value = 2, message = "分类类型参数不正确")
    @Schema(description = "类型 (1:菜品分类, 2:套餐分类)")
    private Integer type;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 32, message = "分类名称长度不能超过32个字符")
    @Schema(description = "分类名称")
    private String name;

    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    @Schema(description = "排序值，数字越小越靠前")
    private Integer sort;

}