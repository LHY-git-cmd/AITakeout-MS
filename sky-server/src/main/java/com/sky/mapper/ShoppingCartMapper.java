package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 购物车数据访问层接口
 * 提供购物车商品的增删改查、按用户清空及批量插入操作
 */
@Mapper
public interface ShoppingCartMapper {

    /**
     * 动态条件查询购物车数据
     *
     * @param shoppingCart 查询条件（含userId、dishId等）
     * @return 购物车列表
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 根据条件查询单条购物车数据
     * 使用动态SQL（XML中实现），根据userId查询购物车记录，
     * 并可选择性地匹配菜品ID、套餐ID和口味
     *
     * @param shoppingCart 查询条件
     * @return 购物车记录
     */
    ShoppingCart getOne(ShoppingCart shoppingCart);

    /**
     * 新增购物车数据
     *
     * @param shoppingCart 购物车实体
     */
    @Insert("insert into shopping_cart " +
            "(name, user_id, dish_id, setmeal_id, dish_flavor, number, amount, image, create_time) " +
            "values " +
            "(#{name}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{image}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 更新购物车商品数量
     *
     * @param shoppingCart 购物车实体（含id、userId、number）
     */
    @Update("update shopping_cart set number = #{number} where id = #{id} and user_id = #{userId}")
    void updateNumber(ShoppingCart shoppingCart);

    /**
     * 查询指定用户的购物车
     *
     * @param userId 用户ID
     * @return 购物车列表
     */
    @Select("select * from shopping_cart where user_id = #{userId} order by create_time desc, id desc")
    List<ShoppingCart> listByUserId(Long userId);

    /**
     * 删除购物车中的一条商品记录
     *
     * @param shoppingCart 购物车实体（含id、userId）
     */
    @Delete("delete from shopping_cart where id = #{id} and user_id = #{userId}")
    void deleteById(ShoppingCart shoppingCart);

    /**
     * 清空指定用户的购物车
     *
     * @param userId 用户ID
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 批量插入购物车数据
     *
     * @param shoppingCartList 购物车列表
     */
    void insertBatch(@Param("shoppingCartList") List<ShoppingCart> shoppingCartList);
}