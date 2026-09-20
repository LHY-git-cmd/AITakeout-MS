package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "登录用户搜索历史写入数据传输对象")
public class SearchHistoryDTO implements Serializable {
    @NotBlank(message = "搜索关键词不能为空")
    @Size(max = 64, message = "搜索关键词不能超过64个字符")
    @Schema(description = "搜索关键词")
    private String keyword;
}