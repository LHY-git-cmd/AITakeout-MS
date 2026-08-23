package com.sky.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付配置属性类
 * 从配置文件中读取sky.wechat前缀下的属性
 */
@Component
@ConfigurationProperties(prefix = "sky.wechat")
@Data
public class WeChatProperties {

    /**
     * 小程序的appid
     */
    private String appid;

    /**
     * 小程序的秘钥
     */
    private String secret;

    /**
     * 商户号
     */
    private String mchid;

    /**
     * 商户API证书的证书序列号
     */
    private String mchSerialNo;

    /**
     * 商户私钥文件路径
     */
    private String privateKeyFilePath;

    /**
     * 证书解密的密钥
     */
    private String apiV3Key;

    /**
     * 平台证书路径
     */
    private String weChatPayCertFilePath;

    /**
     * 支付成功的回调地址
     */
    private String notifyUrl;

    /**
     * 退款成功的回调地址
     */
    private String refundNotifyUrl;

    /**
     * 是否启用模拟支付（开发测试用）
     */
    private Boolean mockPay = false;

}