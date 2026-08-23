package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.WebUserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.properties.WebLoginProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户业务实现类
 * 提供微信登录、Web演示登录和用户查询功能，支持微信openid自动注册
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    /** 微信登录接口地址 */
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WebLoginProperties webLoginProperties;

    /**
     * 微信登录
     * 通过code换取openid，新用户自动注册
     *
     * @param userLoginDTO 微信登录信息（包含code）
     * @return 登录成功的用户实体
     * @throws LoginFailedException 登录失败
     */
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String openid = getOpenid(userLoginDTO.getCode());

        if (openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        User user = userMapper.getByOpenid(openid);

        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            log.info("插入数据{}", JSON.toJSONString(user));
            userMapper.insert(user);
        }

        return user;
    }

    /**
     * 开发环境Web演示登录
     * 校验手机号格式和验证码，支持自动注册新用户
     *
     * @param webUserLoginDTO Web登录信息（含手机号和验证码）
     * @return 登录成功的用户实体
     * @throws LoginFailedException 登录失败
     */
    @Override
    public User webLogin(WebUserLoginDTO webUserLoginDTO) {
        if (!webLoginProperties.isEnabled()) {
            throw new LoginFailedException("Web 演示登录未启用");
        }
        if (webUserLoginDTO == null
                || webUserLoginDTO.getPhone() == null
                || !webUserLoginDTO.getPhone().matches("^1\\d{10}$")) {
            throw new LoginFailedException("请输入正确的手机号");
        }
        if (webLoginProperties.getVerificationCode() == null
                || !webLoginProperties.getVerificationCode().equals(webUserLoginDTO.getCode())) {
            throw new LoginFailedException("验证码错误");
        }

        User user = userMapper.getByPhone(webUserLoginDTO.getPhone());
        if (user == null) {
            user = User.builder()
                    .name("演示用户")
                    .phone(webUserLoginDTO.getPhone())
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }
        return user;
    }

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户实体
     * @throws LoginFailedException 用户不存在
     */
    @Override
    public User getById(Long id) {
        User user = userMapper.getById(id);
        if (user == null) {
            throw new LoginFailedException("用户不存在");
        }
        return user;
    }

    /**
     * 调用微信接口服务，获取微信用户的openid
     * 使用appid、secret、js_code换取openid
     *
     * @param code 微信登录凭证code
     * @return 用户openid，失败返回null
     */
    private String getOpenid(String code) {
        Map<String, String> map = new HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        log.info("微信登录返回：{}", json);
        if (json == null || json.isEmpty()) {
            log.warn("调用微信接口失败，返回为空");
            return null;
        }

        try {
            JSONObject jsonObject = JSON.parseObject(json);
            String openid = jsonObject.getString("openid");
            if (openid == null) {
                String errcode = jsonObject.getString("errcode");
                String errmsg = jsonObject.getString("errmsg");
                log.warn("获取openid失败：errcode={}, errmsg={}", errcode, errmsg);
            }
            return openid;
        } catch (Exception e) {
            log.error("解析微信登录响应失败", e);
            return null;
        }
    }
}