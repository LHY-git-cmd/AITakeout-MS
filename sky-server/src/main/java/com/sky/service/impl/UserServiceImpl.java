package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.WebUserLoginDTO;
import com.sky.dto.WebUserRegisterDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.properties.WebLoginProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    private PasswordEncoder passwordEncoder;

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
     * 使用手机号和密码登录
     *
     * @param webUserLoginDTO Web登录信息（含手机号和密码）
     * @return 登录成功的用户实体
     * @throws LoginFailedException 登录失败
     */
    @Override
    public User webLogin(WebUserLoginDTO webUserLoginDTO) {
        if (!webLoginProperties.isEnabled()) {
            throw new LoginFailedException("Web 演示登录未启用");
        }
        if (webUserLoginDTO == null) {
            throw new LoginFailedException("手机号或密码错误");
        }
        User user = userMapper.getByPhone(webUserLoginDTO.getPhone());
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(webUserLoginDTO.getPassword(), user.getPassword())) {
            throw new LoginFailedException("手机号或密码错误");
        }
        return user;
    }

    @Override
    public User webRegister(WebUserRegisterDTO dto) {
        if (!webLoginProperties.isEnabled()) {
            throw new LoginFailedException("Web 演示登录未启用");
        }
        if (userMapper.getByPhone(dto.getPhone()) != null) {
            throw new LoginFailedException("该手机号已注册");
        }

        User user = User.builder()
                .name(dto.getName().trim())
                .phone(dto.getPhone())
                .password(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD))
                .sex(emptyToNull(dto.getSex()))
                .idNumber(emptyToNull(dto.getIdNumber()))
                .avatar(emptyToNull(dto.getAvatar()))
                .createTime(LocalDateTime.now())
                .build();
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new LoginFailedException("该手机号已注册");
        }
        return user;
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
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
