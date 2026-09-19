package com.sky.mapper;

import com.sky.entity.FundTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/** 资金转移持久化接口。 */
@Mapper
public interface FundTransferMapper {
    int insert(FundTransfer transfer);

    FundTransfer findByBusinessKey(String businessKey);

    default FundTransfer findByBusinessKeyForUpdate(String businessKey) {
        return findByBusinessKey(businessKey);
    }

    FundTransfer findById(Long id);

    @Update("update fund_transfer set operator_id = #{operatorId}, reason = #{reason}, "
            + "adjusted_account_id = #{adjustedAccountId}, balance_before_cent = #{balanceBeforeCent}, "
            + "balance_after_cent = #{balanceAfterCent}, update_time = CURRENT_TIMESTAMP where id = #{id}")
    int updateAudit(FundTransfer transfer);
}
