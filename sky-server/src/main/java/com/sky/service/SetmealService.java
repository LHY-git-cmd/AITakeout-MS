package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;

import java.util.List;

/**
 * 套餐服务接口
 */
public interface SetmealService {

    /**
     * 新增套餐
     * 
     * @param setmealDTO 套餐数据传输对象，包含套餐基本信息和关联菜品列表
     */
    void save(SetmealDTO setmealDTO);

    /**
     * 分页查询套餐
     * 
     * @param setmealPageQueryDTO 分页查询条件
     * @return 分页结果对象
     */
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据ID查询套餐详情（包含分类名称）
     * 
     * @param id 套餐ID
     * @return 套餐详情视图对象
     */
    SetmealVO getByIdWithDish(Long id);

    /**
     * 修改套餐
     * 
     * @param setmealDTO 套餐数据传输对象
     */
    void update(SetmealDTO setmealDTO);

    /**
     * 套餐的起售和停售
     * 
     * @param status 状态值，1表示起售，0表示停售
     * @param id 套餐ID
     */
    void startOrStop(Integer status, Long id);

    /**
     * 删除套餐
     * 
     * @param id 套餐ID
     */
    void deleteById(Long id);

    /**
     * 条件查询套餐列表
     * 
     * @param setmeal 查询条件对象
     * @return 符合条件的套餐列表
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐ID查询菜品选项
     * 
     * @param id 套餐ID
     * @return 菜品选项列表
     */
    List<DishItemVO> getDishItemById(Long id);

}