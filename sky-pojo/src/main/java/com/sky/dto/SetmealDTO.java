package com.sky.dto;

import com.sky.entity.SetmealDish;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "套餐数据传输对象")
public class SetmealDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "套餐分类不能为空")
    @Schema(description = "分类ID")
    private Long categoryId;

    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 64, message = "套餐名称长度不能超过64个字符")
    @Schema(description = "套餐名称")
    private String name;

    @NotNull(message = "套餐价格不能为空")
    @DecimalMin(value = "0.01", message = "套餐价格必须大于0")
    @Schema(description = "套餐价格")
    private BigDecimal price;

    @Schema(description = "状态 (0:停用, 1:启用)", defaultValue = "1")
    private Integer status;

    @Size(max = 255, message = "套餐描述长度不能超过255个字符")
    @Schema(description = "描述信息")
    private String description;

    @NotBlank(message = "套餐图片不能为空")
    @Size(max = 255, message = "套餐图片地址过长")
    @Schema(description = "图片地址")
    private String image;

    @NotEmpty(message = "套餐至少包含一个菜品")
    @Schema(description = "套餐菜品关系列表")
    private List<SetmealDish> setmealDishes = new ArrayList<>();

}