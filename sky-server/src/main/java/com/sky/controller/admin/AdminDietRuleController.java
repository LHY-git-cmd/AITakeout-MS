package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.diet.DietRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端饮食规则集管理接口
 * <p>
 * 提供对饮食规则集的全生命周期管理，包括创建、查询、校验、发布、下线和回滚。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/diet/rule-sets")
@RequiredArgsConstructor
@Tag(name = "饮食规则集管理接口")
public class AdminDietRuleController {
    private final DietRuleService service;

    /**
     * 获取所有饮食规则集列表。
     *
     * @return 规则集列表。
     */
    @GetMapping
    @Operation(summary = "获取规则集列表")
    @RequireAdminPermission(AdminPermission.DIET_RULE_READ)
    public Result<List<Map<String, Object>>> list() {
        log.info("获取所有饮食规则集列表");
        return Result.success(service.list());
    }

    /**
     * 创建一个新的饮食规则集。
     *
     * @param body 包含规则集信息的请求体。
     * @return 创建的规则集。
     */
    @PostMapping
    @Operation(summary = "创建新规则集")
    @RequireAdminPermission(AdminPermission.DIET_RULE_EDIT)
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        log.info("创建新的饮食规则集: {}", body);
        return Result.success(service.create(body, BaseContext.getCurrentId()));
    }

    /**
     * 校验指定的规则集。
     *
     * @param id 规则集ID。
     * @return 成功响应。
     */
    @PostMapping("/{id}/validate")
    @Operation(summary = "校验规则集")
    @RequireAdminPermission(AdminPermission.DIET_DATA_VERIFY)
    public Result<Void> validate(@Parameter(description = "规则集ID") @PathVariable String id) {
        log.info("校验规则集: id={}", id);
        service.validate(id, BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 发布指定的规则集。
     *
     * @param id 规则集ID。
     * @return 成功响应。
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布规则集")
    @RequireAdminPermission(AdminPermission.DIET_RULE_PUBLISH)
    public Result<Void> publish(@Parameter(description = "规则集ID") @PathVariable String id) {
        log.info("发布规则集: id={}", id);
        service.publish(id, BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 下线指定的规则集。
     *
     * @param id 规则集ID。
     * @return 成功响应。
     */
    @PostMapping("/{id}/offline")
    @Operation(summary = "下线规则集")
    @RequireAdminPermission(AdminPermission.DIET_RULE_PUBLISH)
    public Result<Void> offline(@Parameter(description = "规则集ID") @PathVariable String id) {
        log.info("下线规则集: id={}", id);
        service.offline(id, BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 回滚到指定的规则集版本。
     *
     * @param id 规则集ID。
     * @return 成功响应。
     */
    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚规则集")
    @RequireAdminPermission(AdminPermission.DIET_RULE_PUBLISH)
    public Result<Void> rollback(@Parameter(description = "规则集ID") @PathVariable String id) {
        log.info("回滚规则集: id={}", id);
        service.rollback(id, BaseContext.getCurrentId());
        return Result.success();
    }
}