package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
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
     *新增菜品和对应的口味
    *@param dishDTO
     */
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();

        BeanUtils.copyProperties(dishDTO, dish);

        //向菜品表插入1条数据
        dishMapper.insert(dish);

        //获取insert生成的主键属性值
        Long dishId = dish.getId();


        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(flavor -> {
                flavor.setDishId(dishId);
            });
            //向口味表插入n条数据
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 分页查询菜品
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);

        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<Dish> page = dishMapper.pageQuery(dishPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 删除菜品（支持批量删除）
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        log.info("删除菜品：{}", ids);

        for (Long id : ids) {
            // 检查菜品状态
            Integer status = dishMapper.getStatusById(id);
            if (status != null && status == 1) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }

            // 检查是否被套餐关联
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishId(id);
            if (setmealIds != null && !setmealIds.isEmpty()) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
            }
        }

        // 批量删除关联的口味数据
        dishFlavorMapper.deleteByDishIds(ids);

        // 批量删除菜品数据
        dishMapper.deleteByIds(ids);
    }

    /**
     * 根据id查询菜品详情
     * @param id
     * @return
     */
    public DishVO getById(Long id) {
        log.info("查询菜品详情：id={}", id);

        // 查询菜品基础信息
        Dish dish = dishMapper.getById(id);

        // 查询菜品口味信息
        List<DishFlavor> flavors = dishFlavorMapper.getByDishId(id);

        // 封装成DishVO
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(flavors);

        return dishVO;
    }

    /**
     * 修改菜品
     * @param dishDTO
     */
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);

        // 更新菜品基础信息
        dishMapper.update(dish);

        // 删除原有口味
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        // 插入新口味
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && flavors.size() > 0) {
            flavors.forEach(flavor -> {
                flavor.setDishId(dishDTO.getId());
            });
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
   条件查询菜品和口味
   @param dish
   @return
   */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }
        return dishVOList;
    }

    /**
     * 菜品起售/停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        log.info("菜品状态变更：菜品ID={}, 状态={}", id, status);

        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();

        dishMapper.updateStatus(dish);
    }
}