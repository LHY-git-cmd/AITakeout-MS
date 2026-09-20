package com.sky.dto;

import com.sky.entity.DishFlavor;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "菜品数据传输对象")
public class DishDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 64, message = "菜品名称长度不能超过64个字符")
    @Schema(description = "菜品名称")
    private String name;

    @NotNull(message = "菜品分类不能为空")
    @Schema(description = "菜品分类ID")
    private Long categoryId;

    @NotNull(message = "菜品价格不能为空")
    @DecimalMin(value = "0.01", message = "菜品价格必须大于0")
    @Schema(description = "菜品价格")
    private BigDecimal price;

    @NotBlank(message = "菜品图片不能为空")
    @Size(max = 255, message = "菜品图片地址过长")
    @Schema(description = "图片地址")
    private String image;

    @Size(max = 255, message = "菜品描述长度不能超过255个字符")
    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "状态 (0:停售, 1:起售)", defaultValue = "1")
    private Integer status;

    @Schema(description = "菜品口味列表")
    private List<DishFlavor> flavors = new ArrayList<>();

}