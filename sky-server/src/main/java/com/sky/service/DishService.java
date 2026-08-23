package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

/**
 * 菜品业务层接口
 * 提供菜品的CRUD操作、口味管理及起售/停售功能
 */
public interface DishService {

    /**
     * 新增菜品和对应的口味
     *
     * @param dishDTO 菜品数据传输对象（包含口味列表）
     */
    void saveWithFlavor(DishDTO dishDTO);

    /**
     * 分页查询菜品
     *
     * @param dishPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 删除菜品（支持批量删除）
     *
     * @param ids 菜品ID集合
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询菜品详情（包含口味）
     *
     * @param id 菜品ID
     * @return 菜品视图对象
     */
    DishVO getById(Long id);

    /**
     * 修改菜品（包含口味）
     *
     * @param dishDTO 菜品数据传输对象
     */
    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 条件查询菜品和口味列表
     *
     * @param dish 查询条件
     * @return 菜品视图对象列表
     */
    List<DishVO> listWithFlavor(Dish dish);

    /**
     * 菜品起售/停售
     *
     * @param status 状态（0-停售，1-起售）
     * @param id     菜品ID
     */
    void startOrStop(Integer status, Long id);
}