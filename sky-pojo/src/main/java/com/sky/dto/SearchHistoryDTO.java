package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 登录用户搜索历史写入参数。 */
@Data
public class SearchHistoryDTO {
    @NotBlank(message = "搜索关键词不能为空")
    @Size(max = 64, message = "搜索关键词不能超过64个字符")
    private String keyword;
}
