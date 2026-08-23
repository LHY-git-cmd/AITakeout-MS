package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 套餐菜品关系数据访问层接口
 * 提供套餐与菜品关联关系的CRUD操作
 */
@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品id查询关联的套餐ID列表
     *
     * @param dishId 菜品ID
     * @return 套餐ID列表
     */
    List<Long> getSetmealIdsByDishId(Long dishId);

    /**
     * 批量插入套餐菜品关系
     *
     * @param setmealDishes 套餐菜品关系列表
     */
    void insertBatch(@Param("list") List<SetmealDish> setmealDishes);

    /**
     * 根据套餐id删除套餐菜品关系
     *
     * @param setmealId 套餐ID
     */
    @Delete("delete from setmeal_dish where setmeal_id = #{setmealId}")
    void deleteBySetmealId(Long setmealId);

    /**
     * 根据套餐id查询菜品列表
     *
     * @param setmealId 套餐ID
     * @return 套餐菜品关系列表
     */
    List<SetmealDish> getBySetmealId(Long setmealId);

    /**
     * 根据套餐id集合批量删除套餐菜品关系
     *
     * @param setmealIds 套餐ID集合
     */
    void deleteBySetmealIds(@Param("setmealIds") List<Long> setmealIds);
}