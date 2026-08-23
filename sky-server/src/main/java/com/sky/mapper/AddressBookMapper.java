package com.sky.mapper;

import com.sky.entity.AddressBook;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 地址簿数据访问层接口
 * 提供地址簿的CRUD操作及相关查询方法
 */
@Mapper
public interface AddressBookMapper {

    /**
     * 条件查询地址簿列表
     *
     * @param addressBook 查询条件
     * @return 地址簿列表
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 新增地址簿
     *
     * @param addressBook 地址簿实体
     */
    @Insert("insert into address_book" +
            "        (user_id, consignee, phone, sex, province_code, province_name, city_code, cityName, district_code," +
            "         district_name, detail, label, is_default)" +
            "        values (#{userId}, #{consignee}, #{phone}, #{sex}, #{provinceCode}, #{provinceName}, #{cityCode}, #{cityName}," +
            "                #{districtCode}, #{districtName}, #{detail}, #{label}, #{isDefault})")
    void insert(AddressBook addressBook);

    /**
     * 根据id查询地址簿
     *
     * @param id 地址簿ID
     * @return 地址簿实体
     */
    @Select("select * from address_book where id = #{id}")
    AddressBook getById(Long id);

    /**
     * 根据id和用户id查询地址簿
     *
     * @param id     地址簿ID
     * @param userId 用户ID
     * @return 地址簿实体
     */
    @Select("select * from address_book where id = #{id} and user_id = #{userId}")
    AddressBook getByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 根据id修改地址簿
     *
     * @param addressBook 地址簿实体
     */
    void update(AddressBook addressBook);

    /**
     * 根据用户id修改是否默认地址
     *
     * @param addressBook 地址簿实体（包含userId和isDefault）
     */
    @Update("update address_book set is_default = #{isDefault} where user_id = #{userId}")
    void updateIsDefaultByUserId(AddressBook addressBook);

    /**
     * 根据id删除地址簿
     *
     * @param id 地址簿ID
     */
    @Delete("delete from address_book where id = #{id}")
    void deleteById(Long id);

}