package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 菜品口味数据访问层接口
 * 提供菜品口味的CRUD操作，包括批量插入、按菜品ID查询和删除
 */
@Mapper
public interface DishFlavorMapper {

    /**
     * 批量插入口味数据
     *
     * @param flavors 口味列表
     */
    void insertBatch(List<DishFlavor> flavors);

    /**
     * 根据菜品id查询口味数据
     *
     * @param dishId 菜品ID
     * @return 口味列表
     */
    List<DishFlavor> getByDishId(Long dishId);

    /**
     * 根据菜品id删除口味数据
     *
     * @param dishId 菜品ID
     */
    @Delete("delete from dish_flavor where dish_id = #{dishId}")
    void deleteByDishId(Long dishId);

    /**
     * 根据菜品id集合批量删除口味数据
     *
     * @param ids 菜品ID集合
     */
    void deleteByDishIds(List<Long> ids);
}