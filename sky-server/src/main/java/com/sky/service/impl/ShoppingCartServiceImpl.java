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

    @Override
    public List<ShoppingCart> showShoppingCart() {
        return shoppingCartMapper.listByUserId(BaseContext.getCurrentId());
    }

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

    @Override
    @Transactional
    public void cleanShoppingCart() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }

    private ShoppingCart buildQuery(ShoppingCartDTO shoppingCartDTO, Long userId) {
        return ShoppingCart.builder()
                .userId(userId)
                .dishId(shoppingCartDTO.getDishId())
                .setmealId(shoppingCartDTO.getSetmealId())
                .dishFlavor(shoppingCartDTO.getDishFlavor())
                .build();
    }

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
