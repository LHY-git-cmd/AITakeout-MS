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

@RestController
@RequestMapping("/user/shoppingCart")
@Tag(name = "C端-购物车接口")
@Slf4j
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 添加商品到购物车
     * POST /user/shoppingCart/add
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
     * GET /user/shoppingCart/list
     */
    @GetMapping("/list")
    @Operation(summary = "查看购物车")
    public Result<List<ShoppingCart>> list() {
        return Result.success(shoppingCartService.showShoppingCart());
    }

    /**
     * 减少购物车中商品的数量
     * POST /user/shoppingCart/sub
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
     * DELETE /user/shoppingCart/clean
     */
    @DeleteMapping("/clean")
    @Operation(summary = "清空购物车")
    public Result<String> clean() {
        shoppingCartService.cleanShoppingCart();
        return Result.success();
    }
}
