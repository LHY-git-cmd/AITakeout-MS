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
 * 提供套餐的CRUD、起售/停售等功能，起售时校验关联菜品是否在售
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
     * 新增套餐（含关联菜品）
     * 先插入套餐获取主键，再批量插入套餐菜品关联关系
     *
     * @param setmealDTO 套餐数据传输对象，包含套餐基本信息和关联菜品列表
     */
    @Override
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO.getName());

        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmeal.setCreateTime(LocalDateTime.now());
        setmeal.setUpdateTime(LocalDateTime.now());
        setmeal.setCreateUser(BaseContext.getCurrentId());
        setmeal.setUpdateUser(BaseContext.getCurrentId());

        setmealMapper.insert(setmeal);
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(dish -> dish.setSetmealId(setmealId));
            setmealDishMapper.insertBatch(setmealDishes);
        }

        log.info("新增套餐成功，套餐ID：{}", setmealId);
    }

    /**
     * 分页查询套餐
     *
     * @param setmealPageQueryDTO 分页查询条件
     * @return 分页结果对象
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("分页查询套餐：页码={}, 每页数量={}", setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        log.info("分页查询完成，总记录数：{}", page.getTotal());
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据ID查询套餐详情（包含分类名称和菜品列表）
     *
     * @param id 套餐ID
     * @return 套餐详情视图对象
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        log.info("查询套餐详情：套餐ID={}", id);
        SetmealVO setmealVO = setmealMapper.getByIdWithDish(id);
        if (setmealVO != null) {
            List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
            setmealVO.setSetmealDishes(setmealDishes);
        }
        return setmealVO;
    }

    /**
     * 修改套餐（含关联菜品）
     * 先更新套餐基本信息，再删除原有关联并插入新关联
     *
     * @param setmealDTO 套餐数据传输对象
     */
    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        log.info("修改套餐：套餐ID={}", setmealDTO.getId());

        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmeal.setUpdateTime(LocalDateTime.now());
        setmeal.setUpdateUser(BaseContext.getCurrentId());
        setmealMapper.update(setmeal);

        Long setmealId = setmealDTO.getId();
        setmealDishMapper.deleteBySetmealId(setmealId);

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(dish -> dish.setSetmealId(setmealId));
            setmealDishMapper.insertBatch(setmealDishes);
        }

        log.info("修改套餐成功，套餐ID：{}", setmealId);
    }

    /**
     * 套餐起售/停售
     * 起售时校验所有关联菜品是否已启售，存在停售菜品则拒绝起售
     *
     * @param status 状态值，1表示起售，0表示停售
     * @param id     套餐ID
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        log.info("套餐状态变更：套餐ID={}, 状态={}", id, status);

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

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();
        setmealMapper.updateStatus(setmeal);
        log.info("套餐状态变更成功，套餐ID：{}，当前状态：{}", id, status);
    }

    /**
     * 删除套餐
     * 校验套餐是否在售，在售则不允许删除，同时级联删除关联菜品
     *
     * @param id 套餐ID
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        log.info("删除套餐：套餐ID={}", id);
        Setmeal setmeal = setmealMapper.getById(id);
        if (setmeal.getStatus() == StatusConstant.ENABLE) {
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        }
        setmealMapper.deleteById(id);
        setmealDishMapper.deleteBySetmealId(id);
        log.info("删除套餐成功，套餐ID：{}", id);
    }

    /**
     * 批量删除套餐
     * 校验所有套餐是否都已停售，在售则不允许删除
     *
     * @param ids 套餐ID列表
     */
    @Override
    @Transactional
    public void deleteByIds(List<Long> ids) {
        log.info("批量删除套餐：套餐ID列表={}", ids);
        List<Setmeal> setmeals = setmealMapper.getByIds(ids);
        for (Setmeal setmeal : setmeals) {
            if (setmeal.getStatus() == StatusConstant.ENABLE) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        setmealDishMapper.deleteBySetmealIds(ids);
        setmealMapper.deleteByIds(ids);
        log.info("批量删除套餐成功，删除数量：{}", ids.size());
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
     * 根据套餐ID查询菜品选项（用户端展示使用）
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