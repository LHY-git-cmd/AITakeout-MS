package com.sky.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 脱敏后的用户饮食档案视图。 */
@Data
@Builder
public class UserDietProfileVO {
    private String regionCode;
    private String dietaryPattern;
    private String consentVersion;
    private LocalDateTime consentedAt;
    private List<Map<String, Object>> constraints;
    private List<Map<String, Object>> goals;
}
