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
     * 否则新增一条购物车记录
     */
    @Override
    @Transactional
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 校验请求参数
        validateShoppingCartDTO(shoppingCartDTO);

        // 获取当前登录用户ID
        Long userId = BaseContext.getCurrentId();

        // 根据用户ID和商品信息构建查询条件，用于判断购物车中是否已存在该商品
        ShoppingCart query = buildQuery(shoppingCartDTO, userId);
        ShoppingCart existingCart = shoppingCartMapper.getOne(query);

        // 如果购物车中已存在该商品，仅将数量加1
        if (existingCart != null) {
            existingCart.setNumber(existingCart.getNumber() + 1);
            shoppingCartMapper.updateNumber(existingCart);
            return;
        }

        // 构建新的购物车记录
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .dishId(shoppingCartDTO.getDishId())
                .setmealId(shoppingCartDTO.getSetmealId())
                .dishFlavor(shoppingCartDTO.getDishFlavor())
                .number(1)
                .createTime(LocalDateTime.now())
                .build();

        // 根据商品类型（菜品或套餐）查询详细信息并填充
        if (shoppingCartDTO.getDishId() != null) {
            // 添加的是菜品
            Dish dish = dishMapper.getById(shoppingCartDTO.getDishId());
            if (dish == null) {
                throw new ShoppingCartBusinessException("菜品不存在");
            }
            shoppingCart.setName(dish.getName());
            shoppingCart.setAmount(dish.getPrice());
            shoppingCart.setImage(dish.getImage());
        } else {
            // 添加的是套餐
            Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
            if (setmeal == null) {
                throw new ShoppingCartBusinessException("套餐不存在");
            }
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setAmount(setmeal.getPrice());
            shoppingCart.setImage(setmeal.getImage());
        }

        // 插入新的购物车记录
        shoppingCartMapper.insert(shoppingCart);
        log.info("用户添加商品到购物车：userId={}, dishId={}, setmealId={}",
                userId, shoppingCartDTO.getDishId(), shoppingCartDTO.getSetmealId());
    }

    /**
     * 查看当前用户的购物车列表
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        return shoppingCartMapper.listByUserId(userId);
    }

    /**
     * 减少购物车中商品的数量
     * 如果商品数量大于1，则数量减1；
     * 如果数量为1，则删除该条购物车记录
     */
    @Override
    @Transactional
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 校验请求参数
        validateShoppingCartDTO(shoppingCartDTO);

        // 获取当前登录用户ID
        Long userId = BaseContext.getCurrentId();

        // 查询购物车中对应的商品记录
        ShoppingCart shoppingCart = shoppingCartMapper.getOne(buildQuery(shoppingCartDTO, userId));
        if (shoppingCart == null) {
            throw new ShoppingCartBusinessException("购物车中不存在该商品");
        }

        // 数量大于1时，数量减1；数量为1时，直接删除该记录
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