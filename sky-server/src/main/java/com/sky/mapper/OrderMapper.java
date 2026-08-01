package com.sky.mapper;

import com.github.pagehelper.Page;

import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param order
     */
    void insert(Orders order);

    /**
     * 分页条件查询订单
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 根据状态统计订单数量
     * 用于管理端首页统计各状态的订单数量
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);

    /**
     * 查询指定状态且早于指定下单时间的订单
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(@Param("status") Integer status,
                                           @Param("orderTime") LocalDateTime orderTime);

    /**
     * 根据条件汇总订单金额
     *
     * @param map 查询条件
     * @return 订单金额合计
     */
    Double sumByMap(Map<String, Object> map);

    /**
     * 根据条件统计订单数量
     *
     * @param map 查询条件
     * @return 订单数量
     */
    Integer countByMap(Map<String, Object> map);

    /**
     * 查询指定时间范围内的商品销量排名 Top10
     *
     * @param begin 开始时间
     * @param end 结束时间
     * @return 商品销量数据
     */
    List<GoodsSalesDTO> getSalesTop10(@Param("begin") LocalDateTime begin,
                                     @Param("end") LocalDateTime end);

}
