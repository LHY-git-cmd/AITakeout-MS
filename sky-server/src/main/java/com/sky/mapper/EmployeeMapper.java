package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 员工数据访问层接口
 * 提供员工的CRUD操作、登录验证及分页查询
 */
@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     *
     * @param username 用户名
     * @return 员工实体
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    /**
     * 新增员工
     *
     * @param employee 员工实体
     */
    @Insert("insert into employee (name, username, password, phone, sex, id_number, status, role, create_time, update_time, create_user, update_user) " +
            "values " +
            "(#{name},#{username},#{password},#{phone},#{sex},#{idNumber},#{status},#{role},#{createTime},#{updateTime},#{createUser},#{updateUser})")
    @AutoFill(value = OperationType.INSERT)
    void insert(Employee employee);

    /**
     * 分页查询员工
     *
     * @param employeePageQueryDTO 分页查询条件
     * @return 分页结果
     */
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 根据id动态修改员工属性
     *
     * @param employee 员工实体
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Employee employee);

    /**
     * 根据ID查询员工
     *
     * @param id 员工ID
     * @return 员工实体
     */
    @Select("select * from employee where id = #{id}")
    Employee getById(Long id);
}
