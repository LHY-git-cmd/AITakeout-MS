package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.WebUserLoginDTO;
import com.sky.entity.User;

public interface UserService {

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 开发环境 Web 演示登录
     */
    User webLogin(WebUserLoginDTO webUserLoginDTO);

    User getById(Long id);
}
