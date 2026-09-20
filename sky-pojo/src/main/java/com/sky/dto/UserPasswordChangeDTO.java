package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户密码修改数据传输对象")
public class UserPasswordChangeDTO implements Serializable {
    @Schema(description = "当前密码")
    private String currentPassword;
    @Schema(description = "短信验证码")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 72, message = "新密码长度应为8至72位")
    @Schema(description = "新密码")
    private String newPassword;

    @AssertTrue(message = "请输入当前密码或短信验证码")
    public boolean isIdentityProofPresent() {
        return currentPassword != null && !currentPassword.isBlank()
                || code != null && !code.isBlank();
    }
}