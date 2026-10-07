package com.sky.controller.admin;


import com.sky.result.Result;
import com.sky.service.storage.LocalProductImageStorage;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


/**
 * 通用接口控制器（管理端）
 * 提供菜品和套餐图片的本地上传能力
 */
@RestController
@RequestMapping("/admin/common")
@Slf4j
@Tag(name = "通用接口")
public class CommonController {

    @Autowired
    private LocalProductImageStorage imageStorage;

    /**
     * 文件上传
     * 保存图片至运行目录下的相对目录，返回相对访问URL
     *
     * @param file 上传的文件
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    @Operation(summary = "文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("文件上传：{}", file.getOriginalFilename());

        return Result.success(imageStorage.store(file));
    }
}
