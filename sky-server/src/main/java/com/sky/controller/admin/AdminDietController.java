package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.dto.DishNutritionDTO;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.diet.DietRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端菜品饮食信息管理接口
 * <p>
 * 提供对菜品的营养信息、食材、过敏原等数据的维护功能。
 * 包括查询和保存菜品营养信息、审核营养信息、查看数据质量看板以及重新计算套餐营养等。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/diet")
@RequiredArgsConstructor
@Tag(name = "菜品饮食信息管理接口")
public class AdminDietController {
    private final DietRecommendationService service;

    /**
     * 获取指定菜品的营养信息。
     *
     * @param dishId 菜品ID。
     * @return 包含营养信息的Map。
     */
    @GetMapping("/dishes/{dishId}/nutrition")
    @Operation(summary = "获取菜品营养信息")
    @RequireAdminPermission(AdminPermission.DIET_DATA_READ)
    public Result<Map<String, Object>> nutrition(
            @Parameter(description = "菜品ID") @PathVariable long dishId) {
        log.info("获取菜品营养信息: dishId={}", dishId);
        return Result.success(service.getNutrition(dishId));
    }

    /**
     * 保存指定菜品的营养信息。
     *
     * @param dishId 菜品ID。
     * @param body   包含营养信息的DTO。
     * @return 成功响应。
     */
    @PutMapping("/dishes/{dishId}/nutrition")
    @Operation(summary = "保存菜品营养信息")
    @RequireAdminPermission(AdminPermission.DIET_DATA_EDIT)
    public Result<Void> saveNutrition(
            @Parameter(description = "菜品ID") @PathVariable long dishId,
            @Valid @RequestBody DishNutritionDTO body) {
        log.info("保存菜品营养信息: dishId={}, body={}", dishId, body);
        service.saveNutrition(dishId, body, BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 审核指定菜品的营养信息版本。
     *
     * @param dishId         菜品ID。
     * @param profileVersion 营养信息版本号。
     * @return 成功响应。
     */
    @PostMapping("/dishes/{dishId}/nutrition/{profileVersion}/verify")
    @Operation(summary = "审核菜品营养信息")
    @RequireAdminPermission(AdminPermission.DIET_DATA_VERIFY)
    public Result<Void> verify(
            @Parameter(description = "菜品ID") @PathVariable long dishId,
            @Parameter(description = "营养信息版本号") @PathVariable int profileVersion) {
        log.info("审核菜品营养信息: dishId={}, profileVersion={}", dishId, profileVersion);
        service.verifyNutrition(dishId, profileVersion, BaseContext.getCurrentId());
        return Result.success();
    }

    /**
     * 获取饮食数据质量看板信息。
     *
     * @return 包含看板数据的Map。
     */
    @GetMapping("/quality-dashboard")
    @Operation(summary = "获取饮食数据质量看板")
    @RequireAdminPermission(AdminPermission.DIET_AUDIT_READ)
    public Result<Map<String, Object>> qualityDashboard() {
        log.info("获取饮食数据质量看板");
        return Result.success(service.qualityDashboard());
    }

    /**
     * 重新计算指定套餐的营养信息。
     *
     * @param setmealId 套餐ID。
     * @return 包含重新计算后营养信息的Map。
     */
    @PostMapping("/setmeals/{setmealId}/nutrition/recalculate")
    @Operation(summary = "重新计算套餐营养信息")
    @RequireAdminPermission(AdminPermission.DIET_DATA_VERIFY)
    public Result<Map<String, Object>> recalculateSetmeal(
            @Parameter(description = "套餐ID") @PathVariable long setmealId) {
        log.info("重新计算套餐营养信息: setmealId={}", setmealId);
        return Result.success(service.recalculateSetmealNutrition(setmealId));
    }
}