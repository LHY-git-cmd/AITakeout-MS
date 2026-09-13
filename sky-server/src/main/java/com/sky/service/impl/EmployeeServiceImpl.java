package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.enumeration.AdminRole;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 员工业务实现类
 * 提供员工登录、CRUD、账号启用/禁用等功能，支持MD5旧密码自动升级为BCrypt加密
 */
@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 员工登录
     * 校验账号存在性、密码正确性和账号状态，支持MD5旧密码登录后自动升级
     *
     * @param employeeLoginDTO 登录信息（含用户名和密码）
     * @return 登录成功的员工实体
     * @throws AccountNotFoundException  账号不存在
     * @throws PasswordErrorException    密码错误
     * @throws AccountLockedException    账号被锁定
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        Employee employee = employeeMapper.getByUsername(username);

        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        if (!matchesPassword(password, employee.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        if (isLegacyMd5(employee.getPassword())) {
            employeeMapper.update(Employee.builder()
                    .id(employee.getId())
                    .password(passwordEncoder.encode(password))
                    .build());
        }

        return employee;
    }

    /**
     * 新增员工
     * 默认启用状态，密码使用BCrypt加密存储，公共字段由AutoFillAspect自动填充
     *
     * @param employeeDTO 员工数据传输对象
     */
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employee.setStatus(StatusConstant.ENABLE);
        employee.setRole(AdminRole.ADMIN.name());
        employee.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));
        employeeMapper.insert(employee);
    }

    /**
     * 分页查询员工
     *
     * @param employeePageQueryDTO 分页查询条件
     * @return 分页结果
     */
    public PageResult page(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        long total = page.getTotal();
        List<Employee> records = page.getResult();
        return new PageResult(total, records);
    }

    /**
     * 启用/禁用员工账号
     *
     * @param status 状态（0-禁用，1-启用）
     * @param id     员工ID
     */
    public void startOrStop(Integer status, Long id){
        Employee employee = Employee.builder()
                .id(id)
                .status(status)
                .build();
        employeeMapper.update(employee);
    }

    /**
     * 根据ID查询员工
     *
     * @param id 员工ID
     * @return 员工实体
     */
    @Override
    public Employee getById(Long id) {
        return employeeMapper.getById(id);
    }

    /**
     * 编辑员工信息
     * 公共字段由AutoFillAspect自动填充
     *
     * @param employeeDTO 员工数据传输对象
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employeeMapper.update(employee);
    }

    /**
     * 校验密码是否匹配
     * 支持MD5旧密码和BCrypt新密码两种格式
     *
     * @param rawPassword     原始密码
     * @param storedPassword  存储的加密密码
     * @return 是否匹配
     */
    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isLegacyMd5(storedPassword)) {
            String md5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
            return md5.equalsIgnoreCase(storedPassword);
        }
        return passwordEncoder.matches(rawPassword, storedPassword);
    }

    /**
     * 判断是否为MD5旧密码格式（32位十六进制字符串）
     *
     * @param password 存储的密码
     * @return 是否为MD5格式
     */
    private boolean isLegacyMd5(String password) {
        return password != null && password.matches("(?i)^[0-9a-f]{32}$");
    }
}
