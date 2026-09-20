package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;                // 主键
    private String openid;          // 微信用户唯一标识
    private String name;            // 姓名
    private String phone;           // 手机号
    private String password;        // 登录密码 (BCrypt)
    private String sex;             // 性别 0:女, 1:男
    private String idNumber;        // 身份证号
    private String avatar;          // 头像
    private LocalDateTime createTime; // 注册时间
}