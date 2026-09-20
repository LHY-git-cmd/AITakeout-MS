package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
@Schema(description = "C端用户登录数据传输对象")
public class UserLoginDTO implements Serializable {

    @NotBlank(message = "登录凭证不能为空")
    @Size(max = 128, message = "登录凭证长度不正确")
    @Schema(description = "登录凭证（code）")
    private String code;

}