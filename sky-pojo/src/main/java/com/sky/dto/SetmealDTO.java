package com.sky.dto;

import com.sky.entity.SetmealDish;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SetmealDTO implements Serializable {

    private Long id;

    //分类id
    @NotNull(message = "套餐分类不能为空")
    private Long categoryId;

    //套餐名称
    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 64, message = "套餐名称长度不能超过64个字符")
    private String name;

    //套餐价格
    @NotNull(message = "套餐价格不能为空")
    @DecimalMin(value = "0.01", message = "套餐价格必须大于0")
    private BigDecimal price;

    //状态 0:停用 1:启用
    private Integer status;

    //描述信息
    @Size(max = 255, message = "套餐描述长度不能超过255个字符")
    private String description;

    //图片
    @NotBlank(message = "套餐图片不能为空")
    @Size(max = 255, message = "套餐图片地址过长")
    private String image;

    //套餐菜品关系
    @NotEmpty(message = "套餐至少包含一个菜品")
    private List<SetmealDish> setmealDishes = new ArrayList<>();

}
