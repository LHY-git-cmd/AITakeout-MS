package com.sky.service;

import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.result.PageResult;

/**
 * 员工业务层接口
 * 提供员工登录、CRUD操作及启用/禁用账号功能
 */
public interface EmployeeService {

    /**
     * 员工登录
     *
     * @param employeeLoginDTO 登录信息
     * @return 员工实体
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工
     *
     * @param employeeDTO 员工数据传输对象
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询员工
     *
     * @param employeePageQueryDTO 分页查询条件
     * @return 分页结果
     */
    PageResult page(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用/禁用员工账号
     *
     * @param status 状态（0-禁用，1-启用）
     * @param id     员工ID
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据ID查询员工
     *
     * @param id 员工ID
     * @return 员工实体
     */
    Employee getById(Long id);

    /**
     * 编辑员工信息
     *
     * @param employeeDTO 员工数据传输对象
     */
    void update(EmployeeDTO employeeDTO);
}