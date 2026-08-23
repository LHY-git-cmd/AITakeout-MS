package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.AddressBookBusinessException;
import com.sky.constant.MessageConstant;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 地址簿业务实现类
 * 提供地址簿的CRUD操作，所有操作均校验地址归属当前登录用户
 */
@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询地址簿列表
     *
     * @param addressBook 查询条件
     * @return 地址簿列表
     */
    public List<AddressBook> list(AddressBook addressBook) {
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     * 自动关联当前登录用户，新地址默认为非默认地址
     *
     * @param addressBook 地址簿实体
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询地址
     * 校验地址归属当前用户
     *
     * @param id 地址簿ID
     * @return 地址簿实体
     */
    public AddressBook getById(Long id) {
        return getOwnedAddress(id);
    }

    /**
     * 根据id修改地址
     * 校验地址归属当前用户后更新
     *
     * @param addressBook 地址簿实体
     */
    public void update(AddressBook addressBook) {
        getOwnedAddress(addressBook.getId());
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     * 先将当前用户所有地址置为非默认，再将目标地址设为默认，保证唯一性
     *
     * @param addressBook 地址簿实体（含id）
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        getOwnedAddress(addressBook.getId());
        //1、将当前用户的所有地址修改为非默认地址
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据id删除地址
     * 校验地址归属当前用户后删除
     *
     * @param id 地址簿ID
     */
    public void deleteById(Long id) {
        getOwnedAddress(id);
        addressBookMapper.deleteById(id);
    }

    /**
     * 校验地址归属当前登录用户
     *
     * @param id 地址簿ID
     * @return 地址簿实体
     * @throws AddressBookBusinessException 地址不存在或不属于当前用户
     */
    private AddressBook getOwnedAddress(Long id) {
        if (id == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        AddressBook addressBook = addressBookMapper.getByIdAndUserId(id, BaseContext.getCurrentId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        return addressBook;
    }

}