package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 套餐数据访问接口
 */
@Mapper
public interface SetmealMapper {

    /**
     * 根据分类ID查询套餐的数量 
     * 
     * @param id 分类ID
     * @return 套餐数量
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    /**
     * 动态条件查询套餐
     * 
     * @param setmeal 查询条件对象
     * @return 套餐列表
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐ID查询菜品选项
     * 
     * @param setmealId 套餐ID
     * @return 菜品选项列表
     */
    @Select("select d.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
            "where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(Long setmealId);

    /**
     * 分页查询套餐
     * 
     * @param setmealPageQueryDTO 分页查询条件
     * @return 套餐详情列表
     */
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据ID查询套餐
     * 
     * @param id 套餐ID
     * @return 套餐对象
     */
    @Select("select * from setmeal where id = #{id}")
    Setmeal getById(Long id);

    /**
     * 根据ID查询套餐详情（包含分类名称）
     * 
     * @param id 套餐ID
     * @return 套餐详情视图对象
     */
    SetmealVO getByIdWithDish(Long id);

    /**
     * 更新套餐信息
     * 
     * @param setmeal 套餐对象
     */
    void update(Setmeal setmeal);

    /**
     * 更新套餐状态
     * 
     * @param setmeal 套餐对象（包含ID和状态）
     */
    void updateStatus(Setmeal setmeal);

    /**
     * 新增套餐
     * 
     * @param setmeal 套餐对象
     */
    void insert(Setmeal setmeal);

    /**
     * 根据ID删除套餐
     * 
     * @param id 套餐ID
     */
    void deleteById(Long id);

    /**
     * 批量删除套餐
     * 
     * @param ids 套餐ID列表
     */
    void deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 根据ID列表查询套餐列表
     * 
     * @param ids 套餐ID列表
     * @return 套餐列表
     */
    List<Setmeal> getByIds(@Param("ids") List<Long> ids);
}