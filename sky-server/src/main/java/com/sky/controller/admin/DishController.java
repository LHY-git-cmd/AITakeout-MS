package com.sky.controller.admin;


import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;

import java.util.List;
import java.util.Set;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 菜品管理控制器（管理端）
 * 提供菜品的CRUD操作、起售/停售及缓存管理
 */
@RestController
@RequestMapping("/admin/dish")
@Tag(name = "菜品相关接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 新增菜品（含口味）
     *
     * @param dishDTO 菜品数据传输对象
     * @return 操作结果
     */
    @PostMapping
    @Operation(summary = "新增菜品")
    public Result save(@Valid @RequestBody DishDTO dishDTO){
        log.info("新增菜品：{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);

        // 清除该分类下的菜品缓存
        String key = "dish_"+dishDTO.getCategoryId();
        cleanCache(key);

        return Result.success();
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "菜品分页查询")
    public Result<PageResult> page(@Valid DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除菜品（支持批量删除）
     *
     * @param ids 菜品ID集合
     * @return 操作结果
     */
    @DeleteMapping
    @Operation(summary = "删除菜品")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("删除菜品：{}", ids);
        dishService.deleteBatch(ids);

        // 清除所有菜品缓存
        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 根据分类id查询菜品列表
     *
     * @param categoryId 分类ID
     * @return 菜品视图列表
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("查询菜品列表：categoryId={}", categoryId);
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);
        List<DishVO> list = dishService.listWithFlavor(dish);
        return Result.success(list);
    }

    /**
     * 根据id查询菜品详情
     *
     * @param id 菜品ID
     * @return 菜品视图对象
     */
    @GetMapping("/{id:\\d+}")
    @Operation(summary = "根据id查询菜品详情")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("查询菜品详情：id={}", id);
        DishVO dishVO = dishService.getById(id);
        return Result.success(dishVO);
    }

    /**
     * 修改菜品（含口味）
     *
     * @param dishDTO 菜品数据传输对象
     * @return 操作结果
     */
    @PutMapping
    @Operation(summary = "修改菜品")
    public Result update(@Valid @RequestBody DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 菜品起售/停售
     *
     * @param status 状态（0-停售，1-起售）
     * @param id     菜品ID
     * @return 操作结果
     */
    @PostMapping("/status/{status}")
    @Operation(summary = "菜品起售/停售")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("菜品状态变更：菜品ID={}, 状态={}", id, status);
        dishService.startOrStop(status, id);

        cleanCache("dish_*");

        return Result.success();
    }

    /**
     * 清除缓存数据
     *
     * @param pattern 缓存key匹配模式
     */
    private void cleanCache(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }

}