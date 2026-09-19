package com.sky.mapper;

import com.sky.entity.MockAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 模拟账户持久化接口。 */
@Mapper
public interface MockAccountMapper {
    int insert(MockAccount account);

    MockAccount findById(Long id);

    MockAccount findByIdForUpdate(Long id);

    MockAccount findByTypeAndOwner(@Param("accountType") String accountType,
                                   @Param("ownerId") Long ownerId);

    List<MockAccount> listUserAccounts(@Param("userId") Long userId);

    int updateBalances(@Param("id") Long id,
                       @Param("availableCent") Long availableCent,
                       @Param("frozenCent") Long frozenCent,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("updateTime") LocalDateTime updateTime);
}
