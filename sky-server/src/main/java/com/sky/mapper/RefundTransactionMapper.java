package com.sky.mapper;

import com.sky.entity.RefundTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 退款单持久化与条件状态转换接口。 */
@Mapper
public interface RefundTransactionMapper {
    int insert(RefundTransaction refund);
    RefundTransaction findByRefundNo(String refundNo);
    RefundTransaction findByRefundNoForUpdate(String refundNo);
    RefundTransaction findByBusinessKey(String businessKey);
    RefundTransaction findLatestByOrder(Long orderId);
    List<RefundTransaction> findRetryable(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int updateState(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                    @Param("status") String status, @Param("attemptCount") Integer attemptCount,
                    @Param("freezeTransferId") Long freezeTransferId, @Param("releaseTransferId") Long releaseTransferId,
                    @Param("gatewayRefundNo") String gatewayRefundNo, @Param("failureCode") String failureCode,
                    @Param("failureMessage") String failureMessage, @Param("succeededAt") LocalDateTime succeededAt,
                    @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("updateTime") LocalDateTime updateTime);
}
