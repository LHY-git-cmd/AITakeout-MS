package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 地址簿
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressBook implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //用户id
    private Long userId;

    //收货人
    @NotBlank(message = "收货人不能为空")
    @Size(max = 32, message = "收货人长度不能超过32个字符")
    private String consignee;

    //手机号
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    //性别 0 女 1 男
    private String sex;

    //省级区划编号
    private String provinceCode;

    //省级名称
    private String provinceName;

    //市级区划编号
    private String cityCode;

    //市级名称
    private String cityName;

    //区级区划编号
    private String districtCode;

    //区级名称
    private String districtName;

    //详细地址
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址长度不能超过255个字符")
    private String detail;

    //标签
    @Size(max = 32, message = "地址标签长度不能超过32个字符")
    private String label;

    //是否默认 0否 1是
    private Integer isDefault;

    // 地图解析与配送预检结果
    private BigDecimal latitude;            // 纬度
    private BigDecimal longitude;           // 经度
    private String geocodeStatus;           // 地理编码状态
    private String mapProvider;             // 地图提供商
    private Integer distanceMeters;         // 距离（米）
    private Boolean deliverable;            // 是否可配送
    private String validationMessage;       // 验证信息
    private LocalDateTime validatedAt;      // 验证时间
    private String deliveryRuleVersion;     // 配送规则版本
}