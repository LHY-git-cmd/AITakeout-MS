package com.sky.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.io.Serializable;

@Data
public class CategoryPageQueryDTO implements Serializable {

    //页码
    @Min(value = 1, message = "页码必须大于0")
    private int page;

    //每页记录数
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private int pageSize;

    //分类名称
    private String name;

    //分类类型 1菜品分类  2套餐分类
    private Integer type;

}
