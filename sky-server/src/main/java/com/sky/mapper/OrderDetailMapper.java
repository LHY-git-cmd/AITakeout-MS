package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细数据访问接口
 * 提供订单明细的批量插入、单订单查询和多订单批量查询
 */
@Mapper
public interface OrderDetailMapper {

    /**
     * 批量插入订单明细数据
     *
     * @param orderDetailList 订单明细列表
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询订单明细
     *
     * @param orderId 订单ID
     * @return 订单明细列表
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    /**
     * 批量查询多个订单的明细，供订单分页组装使用，避免N+1查询问题
     *
     * @param orderIds 订单ID列表
     * @return 订单明细列表
     */
    List<OrderDetail> getByOrderIds(@Param("orderIds") List<Long> orderIds);
}