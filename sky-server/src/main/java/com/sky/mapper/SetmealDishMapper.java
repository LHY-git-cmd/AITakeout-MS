package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品id查询关联的套餐ID列表
     * @param dishId
     * @return
     */
    List<Long> getSetmealIdsByDishId(Long dishId);
}