package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜品浏览控制器（用户端）
 * 提供根据分类查询菜品功能，数据优先从Redis缓存获取
 */
@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Tag(name = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 根据分类id查询菜品
     * 优先从Redis缓存获取，缓存未命中则查询数据库并回写缓存
     *
     * @param categoryId 分类ID
     * @return 菜品视图列表
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询菜品")
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
        List<DishVO> list = null;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) {
            list = ((List<?>) cached).stream()
                    .filter(DishVO.class::isInstance)
                    .map(DishVO.class::cast)
                    .collect(Collectors.toList());
        }
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