package com.sky.service;

import com.sky.entity.AddressBook;
import java.util.List;

/**
 * 地址簿业务层接口
 * 提供地址簿的CRUD操作及设置默认地址等功能
 */
public interface AddressBookService {

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
    void save(AddressBook addressBook);

    /**
     * 根据id查询地址簿
     *
     * @param id 地址簿ID
     * @return 地址簿实体
     */
    AddressBook getById(Long id);

    /**
     * 根据id修改地址簿
     *
     * @param addressBook 地址簿实体
     */
    void update(AddressBook addressBook);

    /**
     * 设置默认地址
     *
     * @param addressBook 地址簿实体（包含userId和id）
     */
    void setDefault(AddressBook addressBook);

    /**
     * 根据id删除地址簿
     *
     * @param id 地址簿ID
     */
    void deleteById(Long id);

}