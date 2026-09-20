package com.sky.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 用户密码修改请求，可使用当前密码或短信验证码完成身份确认。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordChangeDTO implements Serializable {
    private String currentPassword;
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 72, message = "新密码长度应为8至72位")
    private String newPassword;

    @AssertTrue(message = "请输入当前密码或短信验证码")
    public boolean isIdentityProofPresent() {
        return currentPassword != null && !currentPassword.isBlank()
                || code != null && !code.isBlank();
    }
}
