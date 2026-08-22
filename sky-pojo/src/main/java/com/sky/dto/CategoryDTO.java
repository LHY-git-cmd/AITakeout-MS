package com.sky.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class CategoryDTO implements Serializable {

    //主键
    private Long id;

    //类型 1 菜品分类 2 套餐分类
    @NotNull(message = "分类类型不能为空")
    @Min(value = 1, message = "分类类型参数不正确")
    @Max(value = 2, message = "分类类型参数不正确")
    private Integer type;

    //分类名称
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 32, message = "分类名称长度不能超过32个字符")
    private String name;

    //排序
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;

}
