package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("用户端查询菜品列表：categoryId={}", categoryId);

        // 参数校验
        if (categoryId == null) {
            log.warn("categoryId为空");
            return Result.success(null);
        }

        // 构造Redis中的key 规则dish_+分类id
        String key = "dish_" + categoryId;

        // 查询redis中是否存在菜品数据
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        if (list != null && !list.isEmpty()) {
            // 如果存在，直接返回，无须查询数据库
            log.info("从Redis缓存中获取菜品数据，数量：{}", list.size());
            return Result.success(list);
        }

        // 如果不存在，查询数据库
        log.info("从数据库查询菜品数据");
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE); // 查询起售中的菜品

        list = dishService.listWithFlavor(dish);
        log.info("查询到菜品数量：{}", list != null ? list.size() : 0);

        // 将查询到的数据放入redis中
        if (list != null && !list.isEmpty()) {
            redisTemplate.opsForValue().set(key, list);
            log.info("菜品数据已缓存到Redis，key={}", key);
        }

        return Result.success(list);
    }

}