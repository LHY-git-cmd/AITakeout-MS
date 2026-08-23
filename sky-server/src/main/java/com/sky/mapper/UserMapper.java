package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 用户数据访问层接口
 * 提供用户的CRUD操作及微信登录相关查询
 */
@Mapper
public interface UserMapper {

    /**
     * 根据openid查询微信用户
     *
     * @param openid 微信openid
     * @return 用户实体
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 根据id查询用户
     *
     * @param id 用户ID
     * @return 用户实体
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 根据手机号查询用户
     *
     * @param phone 手机号
     * @return 用户实体
     */
    @Select("select * from user where phone = #{phone} limit 1")
    User getByPhone(String phone);

    /**
     * 新增用户
     *
     * @param user 用户实体
     */
    void insert(User user);

    /**
     * 根据条件统计用户数量
     *
     * @param map 查询条件
     * @return 用户数量
     */
    Integer countByMap(Map<String, Object> map);
}