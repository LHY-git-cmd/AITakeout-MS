package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 用户昵称修改请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateDTO implements Serializable {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称不能超过32个字符")
    private String name;
}
