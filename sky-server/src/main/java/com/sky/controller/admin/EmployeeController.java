package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.annotation.RequireAdminPermission;
import com.sky.enumeration.AdminPermission;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理控制器（管理端）
 * 提供员工登录、登出、CRUD、账号启停用等功能
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Tag(name = "员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 员工登录
     * 校验账号密码后生成JWT令牌返回
     *
     * @param employeeLoginDTO 登录信息
     * @return 登录响应（含token）
     */
    @PostMapping("/login")
    @Operation(summary = "员工登录")
    public Result<EmployeeLoginVO> login(@Valid @RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：username={}", employeeLoginDTO.getUsername());

        Employee employee = employeeService.login(employeeLoginDTO);

        // 登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        claims.put(JwtClaimsConstant.ADMIN_ROLE, employee.getRole());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 员工退出登录
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    @Operation(summary = "员工退出")
    public Result<String> logout() {
        return Result.success();
    }

    /**
     * 新增员工
     *
     * @param employeeDTO 员工信息
     * @return 操作结果
     */
    @PostMapping
    @RequireAdminPermission(AdminPermission.EMPLOYEE_WRITE)
    @Operation(summary = "新增员工")
    public Result save(@Valid @RequestBody EmployeeDTO employeeDTO){
        log.info("新增员工，员工数据：{}",employeeDTO);

        employeeService.save(employeeDTO);

        return Result.success();
    }

    /**
     * 员工分页查询
     *
     * @param employeePageQueryDTO 分页查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "员工信息查询")
    public Result<PageResult> page(@Valid EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工分页查询：{}",employeePageQueryDTO);
        PageResult pageResult = employeeService.page(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用/禁用员工账号
     *
     * @param status 状态（0-禁用，1-启用）
     * @param id     员工ID
     * @return 操作结果
     */
    @PostMapping("/status/{status}")
    @RequireAdminPermission(AdminPermission.EMPLOYEE_WRITE)
    @Operation(summary = "启用禁用员工账号")
    public Result startOrStop(@PathVariable Integer status, Long id){
        log.info("启用禁用员工账号：status={}, id={}", status, id);
        employeeService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 根据ID查询员工
     *
     * @param id 员工ID
     * @return 员工信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询员工")
    public Result<Employee> getById(@PathVariable Long id){
        log.info("根据ID查询员工：id={}", id);
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    /**
     * 编辑员工信息
     *
     * @param employeeDTO 员工信息
     * @return 操作结果
     */
    @PutMapping
    @RequireAdminPermission(AdminPermission.EMPLOYEE_WRITE)
    @Operation(summary = "编辑员工信息")
    public Result update(@Valid @RequestBody EmployeeDTO employeeDTO){
        log.info("编辑员工信息：{}", employeeDTO);
        employeeService.update(employeeDTO);
        return Result.success();
    }

}
