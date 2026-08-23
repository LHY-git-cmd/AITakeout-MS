package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车业务实现类
 * 提供购物车的添加、减少、查询和清空功能，支持菜品和套餐两种商品类型
 */
@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加商品到购物车
     * 如果购物车中已存在相同商品（菜品/套餐及口味一致），则数量加1；
     * 否则新增一条购物车记录，查询商品详情并填充名称、价格、图片
     *
     * @param shoppingCartDTO 购物车数据传输对象
     */
    @Override
    @Transactional
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        validateShoppingCartDTO(shoppingCartDTO);

        Long userId = BaseContext.getCurrentId();
        ShoppingCart query = buildQuery(shoppingCartDTO, userId);
        ShoppingCart existingCart = shoppingCartMapper.getOne(query);

        if (existingCart != null) {
            existingCart.setNumber(existingCart.getNumber() + 1);
            shoppingCartMapper.updateNumber(existingCart);
            return;
        }

        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .dishId(shoppingCartDTO.getDishId())
                .setmealId(shoppingCartDTO.getSetmealId())
                .dishFlavor(shoppingCartDTO.getDishFlavor())
                .number(1)
                .createTime(LocalDateTime.now())
                .build();

        if (shoppingCartDTO.getDishId() != null) {
            Dish dish = dishMapper.getById(shoppingCartDTO.getDishId());
            if (dish == null) {
                throw new ShoppingCartBusinessException("菜品不存在");
            }
            shoppingCart.setName(dish.getName());
            shoppingCart.setAmount(dish.getPrice());
            shoppingCart.setImage(dish.getImage());
        } else {
            Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
            if (setmeal == null) {
                throw new ShoppingCartBusinessException("套餐不存在");
            }
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setAmount(setmeal.getPrice());
            shoppingCart.setImage(setmeal.getImage());
        }

        shoppingCartMapper.insert(shoppingCart);
        log.info("用户添加商品到购物车：userId={}, dishId={}, setmealId={}",
                userId, shoppingCartDTO.getDishId(), shoppingCartDTO.getSetmealId());
    }

    /**
     * 查询当前用户的购物车列表
     *
     * @return 购物车商品列表
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        return shoppingCartMapper.listByUserId(userId);
    }

    /**
     * 减少购物车中商品的数量
     * 数量大于1时减1，数量为1时删除该条记录
     *
     * @param shoppingCartDTO 购物车数据传输对象
     */
    @Override
    @Transactional
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        validateShoppingCartDTO(shoppingCartDTO);

        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = shoppingCartMapper.getOne(buildQuery(shoppingCartDTO, userId));
        if (shoppingCart == null) {
            throw new ShoppingCartBusinessException("购物车中不存在该商品");
        }

        if (shoppingCart.getNumber() > 1) {
            shoppingCart.setNumber(shoppingCart.getNumber() - 1);
            shoppingCartMapper.updateNumber(shoppingCart);
        } else {
            shoppingCartMapper.deleteById(shoppingCart);
        }
    }

    /**
     * 清空当前用户的购物车
     */
    @Override
    @Transactional
    public void cleanShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    /**
     * 根据购物车DTO和用户ID构建查询条件对象
     * 用于在购物车中查找是否已存在相同商品
     *
     * @param shoppingCartDTO 购物车数据传输对象
     * @param userId          用户ID
     * @return 查询条件对象
     */
    private ShoppingCart buildQuery(ShoppingCartDTO shoppingCartDTO, Long userId) {
        return ShoppingCart.builder()
                .userId(userId)
                .dishId(shoppingCartDTO.getDishId())
                .setmealId(shoppingCartDTO.getSetmealId())
                .dishFlavor(shoppingCartDTO.getDishFlavor())
                .build();
    }

    /**
     * 校验购物车请求参数
     * 请求参数不能为空，且菜品ID和套餐ID必须且只能填写一个
     *
     * @param shoppingCartDTO 购物车数据传输对象
     * @throws ShoppingCartBusinessException 参数校验失败
     */
    private void validateShoppingCartDTO(ShoppingCartDTO shoppingCartDTO) {
        if (shoppingCartDTO == null) {
            throw new ShoppingCartBusinessException("购物车参数不能为空");
        }

        boolean hasDish = shoppingCartDTO.getDishId() != null;
        boolean hasSetmeal = shoppingCartDTO.getSetmealId() != null;
        if (hasDish == hasSetmeal) {
            throw new ShoppingCartBusinessException("菜品ID和套餐ID必须且只能填写一个");
        }
    }
}