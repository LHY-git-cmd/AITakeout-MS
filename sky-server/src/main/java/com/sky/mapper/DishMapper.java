package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 菜品数据访问层接口
 * 提供菜品的CRUD操作、分页查询及相关统计方法
 */
@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     *
     * @param categoryId 分类ID
     * @return 菜品数量
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 新增菜品
     *
     * @param dish 菜品实体
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 分页查询菜品
     *
     * @param dishPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据id查询菜品状态
     *
     * @param id 菜品ID
     * @return 菜品状态
     */
    @Select("select status from dish where id = #{id}")
    Integer getStatusById(Long id);

    /**
     * 根据id删除菜品
     *
     * @param id 菜品ID
     */
    void deleteById(Long id);

    /**
     * 根据id集合批量删除菜品
     *
     * @param ids 菜品ID集合
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据id查询菜品
     *
     * @param id 菜品ID
     * @return 菜品实体
     */
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    /**
     * 更新菜品信息
     *
     * @param dish 菜品实体
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Dish dish);

    /**
     * 根据套餐id查询菜品列表
     *
     * @param setmealId 套餐ID
     * @return 菜品列表
     */
    List<Dish> getBySetmealId(Long setmealId);

    /**
     * 动态条件查询菜品
     *
     * @param dish 查询条件
     * @return 菜品列表
     */
    List<Dish> list(Dish dish);

    /**
     * 更新菜品状态
     *
     * @param dish 菜品实体（包含id和status）
     */
    void updateStatus(Dish dish);

    /**
     * 根据条件统计菜品数量
     *
     * @param map 查询条件
     * @return 菜品数量
     */
    Integer countByMap(Map<String, Object> map);
}