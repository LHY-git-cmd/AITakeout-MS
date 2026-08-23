package com.sky.controller.user;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车控制器（用户端）
 * 提供商品添加、减少、查询和清空购物车功能
 */
@RestController
@RequestMapping("/user/shoppingCart")
@Tag(name = "C端-购物车接口")
@Slf4j
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 添加商品到购物车
     *
     * @param shoppingCartDTO 购物车数据
     * @return 操作结果
     */
    @PostMapping("/add")
    @Operation(summary = "添加商品到购物车")
    public Result<String> add(@Valid @RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("添加商品到购物车：{}", shoppingCartDTO);
        shoppingCartService.addShoppingCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 查看当前用户的购物车列表
     *
     * @return 购物车列表
     */
    @GetMapping("/list")
    @Operation(summary = "查看购物车")
    public Result<List<ShoppingCart>> list() {
        return Result.success(shoppingCartService.showShoppingCart());
    }

    /**
     * 减少购物车中商品的数量
     *
     * @param shoppingCartDTO 购物车数据
     * @return 操作结果
     */
    @PostMapping("/sub")
    @Operation(summary = "减少购物车中的商品数量")
    public Result<String> sub(@Valid @RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("减少购物车中的商品数量：{}", shoppingCartDTO);
        shoppingCartService.subShoppingCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 清空当前用户的购物车
     *
     * @return 操作结果
     */
    @DeleteMapping("/clean")
    @Operation(summary = "清空购物车")
    public Result<String> clean() {
        shoppingCartService.cleanShoppingCart();
        return Result.success();
    }
}