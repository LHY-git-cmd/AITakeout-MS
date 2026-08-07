package com.sky.controller.admin;

import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 套餐管理
 */
@RestController
@RequestMapping("/admin/setmeal")
@Tag(name = "套餐相关接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * 新增套餐
     *
     * @param setmealDTO 套餐数据传输对象
     * @return 成功返回空结果
     */
    @PostMapping
    @Operation(summary = "新增套餐")
    @CacheEvict(cacheNames = "setmealCache",key = "#setmealDTO.categoryId")
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        setmealService.save(setmealDTO);
        return Result.success();
    }

    /**
     * 分页查询套餐
     *
     * @param setmealPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询套餐")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("分页查询套餐：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除套餐
     *
     * @param ids 套餐ID列表
     * @return 成功返回空结果
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除套餐")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true)
    public Result deleteByIds(@RequestParam List<Long> ids) {
        log.info("批量删除套餐，ids：{}", ids);
        setmealService.deleteByIds(ids);
        return Result.success();
    }

    /**
     * 根据分类id查询套餐
     *
     * @param categoryId 分类ID
     * @return 套餐列表
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询套餐")
    @Cacheable(cacheNames = "setmealCache", key = "#categoryId")
    public Result<List<Setmeal>> list(Long categoryId) {
        log.info("根据分类id查询套餐，categoryId：{}", categoryId);
        Setmeal setmeal = new Setmeal();
        setmeal.setCategoryId(categoryId);
        setmeal.setStatus(StatusConstant.ENABLE);

        List<Setmeal> list = setmealService.list(setmeal);
        return Result.success(list);
    }

    /**
     * 根据id查询套餐,用于修改页面回显数据
     *
     * @param id 套餐ID
     * @return 菜品选项列表
     */
    @GetMapping("/dish/{id:\\d+}")
    @Operation(summary = "根据套餐id查询包含的菜品列表")
    public Result<SetmealVO> dishList(@PathVariable Long id) {
        log.info("根据套餐id查询包含的菜品列表，id：{}", id);
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐
     *
     * @param setmealDTO 套餐数据传输对象
     * @return 成功返回空结果
     */
    @PutMapping
    @Operation(summary = "修改套餐")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true)
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐：{}", setmealDTO);
        setmealService.update(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐起售、停售
     *
     * @param status 状态值，1表示起售，0表示停售
     * @param id 套餐ID
     * @return 成功返回空结果
     */
    @PostMapping("/status/{status}")
     @Operation(summary = "套餐起售、停售")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true)
    public Result<String> startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("套餐起售、停售，status：{}，id：{}", status, id);
        setmealService.startOrStop(status, id);
        return Result.success();
    }

}