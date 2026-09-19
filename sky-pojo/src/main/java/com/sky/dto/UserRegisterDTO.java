package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/** Phone account registration request. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDTO implements Serializable {
    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") private String phone;
    @NotBlank @Size(min = 6, max = 6) private String code;
    @NotBlank @Size(min = 8, max = 72) private String password;
    @NotBlank @Size(max = 32) private String name;
}
