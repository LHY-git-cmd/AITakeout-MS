package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 根据条件查询单条购物车数据
     */
    ShoppingCart getOne(ShoppingCart shoppingCart);

    /**
     * 新增购物车数据
     */
    @Insert("insert into shopping_cart " +
            "(name, user_id, dish_id, setmeal_id, dish_flavor, number, amount, image, create_time) " +
            "values " +
            "(#{name}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{image}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 更新购物车商品数量
     */
    @Update("update shopping_cart set number = #{number} where id = #{id} and user_id = #{userId}")
    void updateNumber(ShoppingCart shoppingCart);

    /**
     * 查询指定用户的购物车
     */
    @Select("select * from shopping_cart where user_id = #{userId} order by create_time desc, id desc")
    List<ShoppingCart> listByUserId(Long userId);

    /**
     * 删除购物车中的一条商品记录
     */
    @Delete("delete from shopping_cart where id = #{id} and user_id = #{userId}")
    void deleteById(ShoppingCart shoppingCart);

    /**
     * 清空指定用户的购物车
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);
}
