package com.sky.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 用户主动提交的长期饮食档案；临时推荐条件不使用本 DTO 落库。 */
@Data
public class UserDietProfileDTO {
    @Size(max = 32)
    private String regionCode;
    @Size(max = 32)
    private String dietaryPattern;
    @NotBlank
    private String consentVersion;
    @Valid
    private List<ConstraintItem> constraints = new ArrayList<>();
    @Valid
    private List<GoalItem> goals = new ArrayList<>();

    @Data
    public static class ConstraintItem {
        @NotBlank private String type;
        @NotBlank private String code;
        private String severity = "STRICT";
        private String sourceType = "USER";
    }

    @Data
    public static class GoalItem {
        @NotBlank private String code;
        private int priority = 50;
    }
}
