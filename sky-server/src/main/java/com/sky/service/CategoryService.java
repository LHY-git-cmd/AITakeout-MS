package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;
import java.util.List;

/**
 * 分类业务层接口
 * 提供菜品分类和套餐分类的CRUD操作及启用/禁用功能
 */
public interface CategoryService {

    /**
     * 新增分类
     *
     * @param categoryDTO 分类数据传输对象
     */
    void save(CategoryDTO categoryDTO);

    /**
     * 分页查询分类
     *
     * @param categoryPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 根据id删除分类
     *
     * @param id 分类ID
     */
    void deleteById(Long id);

    /**
     * 修改分类
     *
     * @param categoryDTO 分类数据传输对象
     */
    void update(CategoryDTO categoryDTO);

    /**
     * 启用、禁用分类
     *
     * @param status 状态（0-禁用，1-启用）
     * @param id     分类ID
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据类型查询分类列表
     *
     * @param type 类型（1-菜品分类，2-套餐分类）
     * @return 分类列表
     */
    List<Category> list(Integer type);
}