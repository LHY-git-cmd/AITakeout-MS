package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户个人信息（主要是昵称）修改数据传输对象")
public class UserProfileUpdateDTO implements Serializable {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称不能超过32个字符")
    @Schema(description = "昵称")
    private String name;
}