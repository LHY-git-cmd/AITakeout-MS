package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        String openid = getOpenid(userLoginDTO.getCode());
        
        if (openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //判断是否为新用户
        User user = userMapper.getByOpenid(openid);

        //是新用户完成注册
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
        调用微信接口服务，获取微信用户的openid
        @param code
        @return
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