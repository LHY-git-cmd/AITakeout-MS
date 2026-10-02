package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.dto.DishNutritionDTO;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.diet.DietRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端菜品营养、食材与过敏原维护 API。 */
@RestController
@RequestMapping("/admin/diet")
@RequiredArgsConstructor
public class AdminDietController {
    private final DietRecommendationService service;

    @GetMapping("/dishes/{dishId}/nutrition")
    @RequireAdminPermission(AdminPermission.DIET_DATA_READ)
    public Result<Map<String, Object>> nutrition(@PathVariable long dishId) {
        return Result.success(service.getNutrition(dishId));
    }

    @PutMapping("/dishes/{dishId}/nutrition")
    @RequireAdminPermission(AdminPermission.DIET_DATA_EDIT)
    public Result<Void> saveNutrition(@PathVariable long dishId,
                                      @Valid @RequestBody DishNutritionDTO body) {
        service.saveNutrition(dishId, body, BaseContext.getCurrentId());
        return Result.success();
    }

    @PostMapping("/dishes/{dishId}/nutrition/{profileVersion}/verify")
    @RequireAdminPermission(AdminPermission.DIET_DATA_VERIFY)
    public Result<Void> verify(@PathVariable long dishId, @PathVariable int profileVersion) {
        service.verifyNutrition(dishId, profileVersion, BaseContext.getCurrentId());
        return Result.success();
    }

    @GetMapping("/quality-dashboard")
    @RequireAdminPermission(AdminPermission.DIET_AUDIT_READ)
    public Result<Map<String, Object>> qualityDashboard() {
        return Result.success(service.qualityDashboard());
    }

    @PostMapping("/setmeals/{setmealId}/nutrition/recalculate")
    @RequireAdminPermission(AdminPermission.DIET_DATA_VERIFY)
    public Result<Map<String, Object>> recalculateSetmeal(@PathVariable long setmealId) {
        return Result.success(service.recalculateSetmealNutrition(setmealId));
    }
}
