package com.sky.mapper;

import com.sky.entity.AfterSaleRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 售后申请持久化接口。 */
@Mapper
public interface AfterSaleRequestMapper {
    int insert(AfterSaleRequest request);
    AfterSaleRequest findById(Long id);
    AfterSaleRequest findByIdForUpdate(Long id);
    AfterSaleRequest findActiveByOrderId(Long orderId);
    AfterSaleRequest findLatestByOrderAndUser(@Param("orderId") Long orderId, @Param("userId") Long userId);
    List<AfterSaleRequest> listByUser(@Param("userId") Long userId, @Param("beforeId") Long beforeId, @Param("limit") int limit);
    List<AfterSaleRequest> listForAdmin(@Param("status") String status, @Param("beforeId") Long beforeId, @Param("limit") int limit);
    int updateReview(AfterSaleRequest request);
    int updateRefund(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                     @Param("status") String status, @Param("refundNo") String refundNo,
                     @Param("updateTime") java.time.LocalDateTime updateTime);
    int updateByRefundNo(@Param("refundNo") String refundNo, @Param("status") String status,
                         @Param("updateTime") java.time.LocalDateTime updateTime);
}
