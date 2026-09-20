package com.sky.mapper;

import com.sky.entity.OrderSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderSubmissionMapper {
    int insertIgnore(OrderSubmission submission);
    OrderSubmission find(@Param("userId") Long userId, @Param("key") String key);
    int attachOrder(@Param("id") Long id, @Param("orderId") Long orderId);
}
