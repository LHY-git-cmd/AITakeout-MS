package com.sky.mapper;

import com.sky.entity.PaymentTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 支付单持久化与条件状态转换接口。 */
@Mapper
public interface PaymentTransactionMapper {
    int insert(PaymentTransaction payment);

    PaymentTransaction findByPaymentNo(String paymentNo);

    PaymentTransaction findByPaymentNoForUpdate(String paymentNo);

    PaymentTransaction findByIdempotencyKey(String idempotencyKey);

    PaymentTransaction findLatestByOrderAndStatus(@Param("orderId") Long orderId,
                                                   @Param("status") String status);

    List<PaymentTransaction> findProcessing(@Param("limit") int limit);

    List<PaymentTransaction> findExpiredForUpdate(@Param("now") LocalDateTime now,
                                                   @Param("limit") int limit);

    int updateState(@Param("id") Long id,
                    @Param("expectedStatus") String expectedStatus,
                    @Param("status") String status,
                    @Param("gatewayTradeNo") String gatewayTradeNo,
                    @Param("callbackEventId") String callbackEventId,
                    @Param("failureCode") String failureCode,
                    @Param("succeededAt") LocalDateTime succeededAt,
                    @Param("updateTime") LocalDateTime updateTime);
}
