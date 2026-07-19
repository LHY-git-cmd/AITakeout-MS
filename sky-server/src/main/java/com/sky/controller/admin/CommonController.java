package com.sky.controller.admin;


import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Slf4j
@Api(tags = "通用接口")
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 文件上传
     *
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("文件上传：{}", file.getOriginalFilename());

        try {
            // 获取原始文件名
            String originalFilename = file.getOriginalFilename();
            // 生成UUID作为文件名称
            String uuid = UUID.randomUUID().toString();
            // 获取文件后缀名
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            // 拼接新的文件名
            String fileName = uuid + suffix;

            // 调用OSS工具类上传文件
            String url = aliOssUtil.upload(file.getBytes(), fileName);

            return Result.success(url);
        } catch (IOException e) {
            log.error("文件上传失败", e);

        }
        return Result.error("文件上传失败");
    }
}