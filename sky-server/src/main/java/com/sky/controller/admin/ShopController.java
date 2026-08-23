package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺管理控制器（管理端）
 * 提供店铺营业状态的设置和查询，状态存储于Redis中
 */
@RestController
@RequestMapping("/admin/shop")
@Tag(name = "店铺管理")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置店铺营业状态
     *
     * @param status 营业状态（0-打烊，1-营业中）
     * @return 操作结果
     */
    @PutMapping("/{status}")
    @Operation(summary = "设置店铺营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置店铺状态为：{}", status == 1 ? "营业中" : "打烊");
        redisTemplate.opsForValue().set(KEY, status);
        return Result.success();
    }

    /**
     * 获取店铺的营业状态
     *
     * @return 营业状态（0-打烊，1-营业中）
     */
    @GetMapping("/status")
    @Operation(summary = "查询店铺营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        if (status == null) {
            status = 1;
            redisTemplate.opsForValue().set(KEY, status);
        }
        log.info("查询店铺状态为:{}", status==1?"营业中":"打烊中");
        return Result.success(status);
    }
}