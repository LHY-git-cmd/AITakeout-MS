package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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

    @Select("select * from user where id = #{id} for update")
    User getByIdForUpdate(Long id);

    /**
     * 根据手机号查询用户
     *
     * @param phone 手机号
     * @return 用户实体
     */
    @Select("select * from user where phone = #{phone} limit 1")
    User getByPhone(String phone);

    @Select("select * from user where phone = #{phone} limit 1 for update")
    User getByPhoneForUpdate(String phone);

    /**
     * 新增用户
     *
     * @param user 用户实体
     */
    void insert(User user);

    /** 修改用户昵称。 */
    @Update("update user set name = #{name} where id = #{userId}")
    int updateName(@Param("userId") Long userId, @Param("name") String name);

    /** 修改用户头像地址。 */
    @Update("update user set avatar = #{avatar} where id = #{userId}")
    int updateAvatar(@Param("userId") Long userId, @Param("avatar") String avatar);

    /** 换绑用户手机号。 */
    @Update("update user set phone = #{phone} where id = #{userId}")
    int updatePhone(@Param("userId") Long userId, @Param("phone") String phone);

    /** 修改用户登录密码。 */
    @Update("update user set password = #{password} where id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    /**
     * 根据条件统计用户数量
     *
     * @param map 查询条件
     * @return 用户数量
     */
    Integer countByMap(Map<String, Object> map);
}
