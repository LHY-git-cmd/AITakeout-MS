package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.WebUserLoginDTO;
import com.sky.entity.User;

/**
 * 用户业务层接口
 * 提供微信登录、Web演示登录及用户查询功能
 */
public interface UserService {

    /**
     * 微信登录
     *
     * @param userLoginDTO 微信登录信息（包含code）
     * @return 用户实体
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 开发环境 Web 演示登录
     *
     * @param webUserLoginDTO Web登录信息
     * @return 用户实体
     */
    User webLogin(WebUserLoginDTO webUserLoginDTO);

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户实体
     */
    User getById(Long id);
}