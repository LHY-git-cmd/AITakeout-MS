package com.sky.mapper;

import com.sky.entity.AccountLedgerEntry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 不可变账户分录持久化接口。 */
@Mapper
public interface AccountLedgerEntryMapper {
    int insert(AccountLedgerEntry entry);

    List<AccountLedgerEntry> findByTransferId(Long transferId);

    List<AccountLedgerEntry> findByAccountId(Long accountId);
}
