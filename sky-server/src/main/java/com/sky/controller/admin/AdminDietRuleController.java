package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.diet.DietRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 管理端食养规则版本控制 API。 */
@RestController
@RequestMapping("/admin/diet/rule-sets")
@RequiredArgsConstructor
public class AdminDietRuleController {
    private final DietRuleService service;

    @GetMapping
    @RequireAdminPermission(AdminPermission.DIET_RULE_READ)
    public Result<List<Map<String, Object>>> list() { return Result.success(service.list()); }

    @PostMapping
    @RequireAdminPermission(AdminPermission.DIET_RULE_EDIT)
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.success(service.create(body, BaseContext.getCurrentId()));
    }

    @PostMapping("/{id}/validate")
    @RequireAdminPermission(AdminPermission.DIET_DATA_VERIFY)
    public Result<Void> validate(@PathVariable String id) { service.validate(id, BaseContext.getCurrentId()); return Result.success(); }

    @PostMapping("/{id}/publish")
    @RequireAdminPermission(AdminPermission.DIET_RULE_PUBLISH)
    public Result<Void> publish(@PathVariable String id) { service.publish(id, BaseContext.getCurrentId()); return Result.success(); }

    @PostMapping("/{id}/offline")
    @RequireAdminPermission(AdminPermission.DIET_RULE_PUBLISH)
    public Result<Void> offline(@PathVariable String id) { service.offline(id, BaseContext.getCurrentId()); return Result.success(); }

    @PostMapping("/{id}/rollback")
    @RequireAdminPermission(AdminPermission.DIET_RULE_PUBLISH)
    public Result<Void> rollback(@PathVariable String id) { service.rollback(id, BaseContext.getCurrentId()); return Result.success(); }
}
