package com.sky.dto;

import com.sky.entity.DishFlavor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DishDTO implements Serializable {

    private Long id;
    //菜品名称
    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 64, message = "菜品名称长度不能超过64个字符")
    private String name;
    //菜品分类id
    @NotNull(message = "菜品分类不能为空")
    private Long categoryId;
    //菜品价格
    @NotNull(message = "菜品价格不能为空")
    @DecimalMin(value = "0.01", message = "菜品价格必须大于0")
    private BigDecimal price;
    //图片
    @NotBlank(message = "菜品图片不能为空")
    @Size(max = 255, message = "菜品图片地址过长")
    private String image;
    //描述信息
    @Size(max = 255, message = "菜品描述长度不能超过255个字符")
    private String description;
    //0 停售 1 起售
    private Integer status;
    //口味
    private List<DishFlavor> flavors = new ArrayList<>();

}
