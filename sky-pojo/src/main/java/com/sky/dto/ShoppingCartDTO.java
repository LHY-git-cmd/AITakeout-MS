package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;

@Data
@Schema(description = "购物车数据传输对象")
public class ShoppingCartDTO implements Serializable {

    @Schema(description = "菜品ID")
    private Long dishId;

    @Schema(description = "套餐ID")
    private Long setmealId;

    @Size(max = 100, message = "菜品口味长度不能超过100个字符")
    @Schema(description = "菜品口味")
    private String dishFlavor;

    @JsonIgnore
    @AssertTrue(message = "菜品ID和套餐ID必须且只能填写一个")
    public boolean isTargetValid() {
        return (dishId == null) != (setmealId == null);
    }

}