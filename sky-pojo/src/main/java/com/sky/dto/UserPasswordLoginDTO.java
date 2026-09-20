package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/** Phone/password login request. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordLoginDTO implements Serializable {
    @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") private String phone;
    @NotBlank private String password;
}
