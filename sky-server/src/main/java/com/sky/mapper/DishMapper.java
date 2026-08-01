package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     *插入菜品数据
     * @param dish
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 分页查询菜品
     * @param dishPageQueryDTO
     * @return
     */
    Page<Dish> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据id查询菜品状态
     * @param id
     * @return
     */
    @Select("select status from dish where id = #{id}")
    Integer getStatusById(Long id);

    /**
     * 根据id删除菜品
     * @param id
     */
    void deleteById(Long id);

    /**
     * 根据id集合批量删除菜品
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    /**
     * 更新菜品信息
     * @param dish
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Dish dish);

    /**
     * 根据套餐id查询菜品列表
     * @param setmealId
     * @return
     */
    List<Dish> getBySetmealId(Long setmealId);

    /**
     * 动态条件查询菜品
     * @param dish
     * @return
     */
    List<Dish> list(Dish dish);

    /**
     * 更新菜品状态
     * @param dish
     */
    void updateStatus(Dish dish);

    /**
     * 根据条件统计菜品数量
     * @param map 查询条件
     * @return 菜品数量
     */
    Integer countByMap(Map<String, Object> map);
}
