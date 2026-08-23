package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.Setmeal;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.sky.constant.StatusConstant.DISABLE;

/**
 * 菜品业务实现类
 * 提供菜品的CRUD、口味管理、起售/停售等功能，停售菜品时自动停用关联套餐
 */
@Slf4j
@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 新增菜品和对应的口味
     * 先插入菜品获取主键，再批量插入口味
     *
     * @param dishDTO 菜品数据传输对象（包含口味列表）
     */
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);
        Long dishId = dish.getId();

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(flavor -> flavor.setDishId(dishId));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 分页查询菜品
     *
     * @param dishPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除菜品
     * 校验菜品是否在售或被套餐关联，删除时级联删除口味数据
     *
     * @param ids 菜品ID集合
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        log.info("删除菜品：{}", ids);

        for (Long id : ids) {
            Integer status = dishMapper.getStatusById(id);
            if (status != null && status == 1) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }

            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishId(id);
            if (setmealIds != null && !setmealIds.isEmpty()) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
            }
        }

        dishFlavorMapper.deleteByDishIds(ids);
        dishMapper.deleteByIds(ids);
    }

    /**
     * 根据id查询菜品详情（包含口味列表）
     *
     * @param id 菜品ID
     * @return 菜品视图对象
     */
    public DishVO getById(Long id) {
        log.info("查询菜品详情：id={}", id);
        Dish dish = dishMapper.getById(id);
        List<DishFlavor> flavors = dishFlavorMapper.getByDishId(id);
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(flavors);
        return dishVO;
    }

    /**
     * 修改菜品（包含口味）
     * 先更新菜品基础信息，再删除原有口味并插入新口味
     *
     * @param dishDTO 菜品数据传输对象
     */
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(flavor -> flavor.setDishId(dishDTO.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 条件查询菜品和口味列表
     * 用于用户端展示，返回启售状态的菜品及其口味
     *
     * @param dish 查询条件
     * @return 菜品视图对象列表
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);
        List<DishVO> dishVOList = new ArrayList<>();
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());
            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }
        return dishVOList;
    }

    /**
     * 菜品起售/停售
     * 停售时自动将关联套餐一并停用，保证数据一致性
     *
     * @param status 状态（0-停售，1-起售）
     * @param id     菜品ID
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        log.info("菜品状态变更：菜品ID={}, 状态={}", id, status);
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        dishMapper.updateStatus(dish);

        if (DISABLE.equals(status)) {
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishId(id);
            if (setmealIds != null && !setmealIds.isEmpty()) {
                setmealIds.forEach(setmealId -> setmealMapper.update(
                        Setmeal.builder()
                                .id(setmealId)
                                .status(DISABLE)
                                .build()));
            }
        }
    }
}