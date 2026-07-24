package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 套餐业务实现类
 */
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐
     * 
     * @param setmealDTO 套餐数据传输对象，包含套餐基本信息和关联菜品列表
     */
    @Override
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO.getName());
        
        // 将DTO转换为实体对象
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmeal.setCreateTime(LocalDateTime.now());
        setmeal.setUpdateTime(LocalDateTime.now());
        setmeal.setCreateUser(BaseContext.getCurrentId());
        setmeal.setUpdateUser(BaseContext.getCurrentId());

        // 插入套餐基本信息
        setmealMapper.insert(setmeal);

        // 获取新插入的套餐ID
        Long setmealId = setmeal.getId();
        
        // 处理套餐菜品关联关系
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(dish -> {
                dish.setSetmealId(setmealId);
                dish.setCreateTime(LocalDateTime.now());
                dish.setUpdateTime(LocalDateTime.now());
                dish.setCreateUser(BaseContext.getCurrentId());
                dish.setUpdateUser(BaseContext.getCurrentId());
            });
            // 批量插入套餐菜品关联
            setmealDishMapper.insertBatch(setmealDishes);
        }
        
        log.info("新增套餐成功，套餐ID：{}", setmealId);
    }

    /**
     * 分页查询套餐
     * 
     * @param setmealPageQueryDTO 分页查询条件，包含页码、每页数量、查询关键词等
     * @return 分页结果对象，包含总记录数和当前页数据列表
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("分页查询套餐：页码={}, 每页数量={}", setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        
        // 使用PageHelper进行分页
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = (Page<SetmealVO>) setmealMapper.pageQuery(setmealPageQueryDTO);
        
        log.info("分页查询完成，总记录数：{}", page.getTotal());
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据ID查询套餐详情（包含分类名称）
     * 
     * @param id 套餐ID
     * @return 套餐详情视图对象，包含套餐信息和分类名称
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        log.info("查询套餐详情：套餐ID={}", id);
        return setmealMapper.getByIdWithDish(id);
    }

    /**
     * 修改套餐信息
     * 
     * @param setmealDTO 套餐数据传输对象，包含更新后的套餐信息和菜品列表
     */
    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        log.info("修改套餐：套餐ID={}", setmealDTO.getId());
        
        // 将DTO转换为实体对象
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmeal.setUpdateTime(LocalDateTime.now());
        setmeal.setUpdateUser(BaseContext.getCurrentId());

        // 更新套餐基本信息
        setmealMapper.update(setmeal);

        // 获取套餐ID
        Long setmealId = setmealDTO.getId();
        
        // 删除原有的套餐菜品关联
        setmealDishMapper.deleteBySetmealId(setmealId);

        // 插入新的套餐菜品关联
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(dish -> {
                dish.setSetmealId(setmealId);
                dish.setCreateTime(LocalDateTime.now());
                dish.setUpdateTime(LocalDateTime.now());
                dish.setCreateUser(BaseContext.getCurrentId());
                dish.setUpdateUser(BaseContext.getCurrentId());
            });
            setmealDishMapper.insertBatch(setmealDishes);
        }
        
        log.info("修改套餐成功，套餐ID：{}", setmealId);
    }

    /**
     * 套餐起售/停售
     * 
     * @param status 状态值，1表示起售，0表示停售
     * @param id 套餐ID
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        log.info("套餐状态变更：套餐ID={}, 状态={}", id, status);
        
        // 如果是起售操作，需要检查关联菜品是否都已启售
        if (status == StatusConstant.ENABLE) {
            List<Dish> dishes = dishMapper.getBySetmealId(id);
            if (dishes != null && !dishes.isEmpty()) {
                for (Dish dish : dishes) {
                    if (dish.getStatus() == StatusConstant.DISABLE) {
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                }
            }
        }

        // 构建更新对象
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();
        
        // 更新状态
        setmealMapper.updateStatus(setmeal);
        
        log.info("套餐状态变更成功，套餐ID：{}，当前状态：{}", id, status);
    }

    /**
     * 删除套餐
     * 
     * @param id 套餐ID
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        log.info("删除套餐：套餐ID={}", id);
        
        // 查询套餐信息
        Setmeal setmeal = setmealMapper.getById(id);
        
        // 检查套餐是否在售
        if (setmeal.getStatus() == StatusConstant.ENABLE) {
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        }

        // 删除套餐基本信息
        setmealMapper.deleteById(id);
        
        // 删除套餐菜品关联
        setmealDishMapper.deleteBySetmealId(id);
        
        log.info("删除套餐成功，套餐ID：{}", id);
    }

    /**
     * 条件查询套餐列表
     * 
     * @param setmeal 查询条件对象
     * @return 符合条件的套餐列表
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        log.info("条件查询套餐列表：{}", setmeal);
        return setmealMapper.list(setmeal);
    }

    /**
     * 根据套餐ID查询菜品选项
     * 
     * @param id 套餐ID
     * @return 菜品选项列表
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        log.info("查询套餐菜品选项：套餐ID={}", id);
        return setmealMapper.getDishItemBySetmealId(id);
    }
}