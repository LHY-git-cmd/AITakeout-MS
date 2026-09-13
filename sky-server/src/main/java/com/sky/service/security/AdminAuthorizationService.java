package com.sky.service.security;

import com.sky.entity.Employee;
import com.sky.enumeration.AdminPermission;
import com.sky.enumeration.AdminRole;
import com.sky.exception.PermissionDeniedException;
import com.sky.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * 管理端角色解析与权限裁决的唯一服务入口。
 * 该服务负责解析管理员的角色，并根据角色判断其是否拥有特定操作的权限。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthorizationService {

    /**
     * 普通管理员拥有的权限集合。
     * 使用 EnumSet 来高效地存储和查询权限。
     */
    private static final Set<AdminPermission> ADMIN_PERMISSIONS = EnumSet.of(
            AdminPermission.EMPLOYEE_READ,
            AdminPermission.KNOWLEDGE_READ,
            AdminPermission.ORDER_READ,
            AdminPermission.ORDER_STATUS_WRITE,
            AdminPermission.DISH_READ,
            AdminPermission.DISH_WRITE,
            AdminPermission.SETMEAL_READ,
            AdminPermission.SETMEAL_WRITE,
            AdminPermission.SHOP_READ,
            AdminPermission.SHOP_STATUS_WRITE,
            AdminPermission.WORKSPACE_READ,
            AdminPermission.REPORT_READ
    );

    private final EmployeeMapper employeeMapper;

    /**
     * 根据员工ID解析其对应的管理员角色。
     *
     * @param employeeId 员工ID
     * @return 对应的管理员角色
     * @throws PermissionDeniedException 如果员工不存在，则抛出权限拒绝异常
     */
    public AdminRole resolveRole(Long employeeId) {
        Employee employee = employeeId == null ? null : employeeMapper.getById(employeeId);
        if (employee == null) {
            throw new PermissionDeniedException("管理员身份不存在或已失效");
        }
        if (Integer.valueOf(0).equals(employee.getStatus())) {
            throw new PermissionDeniedException("管理员账号已停用");
        }
        return AdminRole.fromDatabase(employee.getRole());
    }

    /**
     * 判断指定角色是否拥有特定权限。
     * 超级管理员拥有所有权限。
     *
     * @param role       管理员角色
     * @param permission 需要判断的权限
     * @return 如果拥有权限，则返回 true；否则返回 false
     */
    public boolean hasPermission(AdminRole role, AdminPermission permission) {
        return role == AdminRole.SUPER_ADMIN || ADMIN_PERMISSIONS.contains(permission);
    }

    /**
     * 要求指定员工ID必须拥有特定权限，否则抛出异常。
     *
     * @param employeeId 员工ID
     * @param permission 需要的权限
     * @throws PermissionDeniedException 如果不拥有权限，则抛出异常
     */
    public void require(Long employeeId, AdminPermission permission) {
        require(resolveRole(employeeId), permission);
    }

    /**
     * 要求指定角色必须拥有特定权限，否则抛出异常。
     *
     * @param role       管理员角色
     * @param permission 需要的权限
     * @throws PermissionDeniedException 如果不拥有权限，则抛出异常
     */
    public void require(AdminRole role, AdminPermission permission) {
        if (!hasPermission(role, permission)) {
            throw new PermissionDeniedException("当前管理员无权执行该操作");
        }
    }
}
