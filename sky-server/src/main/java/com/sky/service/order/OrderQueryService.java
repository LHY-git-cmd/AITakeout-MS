package com.sky.service.order;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.result.PageResult;
import com.sky.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单查询服务
 * 负责订单的分页查询、详情查询等只读操作，采用批量查询优化N+1问题
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;

    /**
     * 分页查询订单
     *
     * @param query           分页查询条件
     * @param includeDetails  是否包含订单明细（管理端列表不需要，用户端需要）
     * @return 分页结果
     */
    public PageResult page(OrdersPageQueryDTO query, boolean includeDetails) {
        PageHelper.startPage(query.getPage(), query.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(query);
        return new PageResult(page.getTotal(), buildList(page.getResult(), includeDetails));
    }

    /**
     * 根据id查询订单，不存在则抛出异常
     *
     * @param id 订单ID
     * @return 订单实体
     */
    public Orders getExisting(Long id) {
        if (id == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        Orders order = orderMapper.getById(id);
        if (order == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        return order;
    }

    /**
     * 查询订单详情（包含明细）
     *
     * @param order 订单实体
     * @return 订单视图对象
     */
    public OrderVO details(Orders order) {
        return build(order, orderDetailMapper.getByOrderId(order.getId()), true);
    }

    /**
     * 批量构建订单视图列表
     * 通过批量查询订单明细避免N+1查询问题
     *
     * @param orders         订单列表
     * @param includeDetails 是否包含明细
     * @return 订单视图列表
     */
    private List<OrderVO> buildList(List<Orders> orders, boolean includeDetails) {
        if (CollectionUtils.isEmpty(orders)) return Collections.emptyList();
        List<Long> ids = orders.stream().map(Orders::getId).toList();
        // 批量查询所有订单的明细，按orderId分组
        Map<Long, List<OrderDetail>> grouped = orderDetailMapper.getByOrderIds(ids).stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));
        return orders.stream()
                .map(order -> build(order, grouped.getOrDefault(order.getId(), Collections.emptyList()), includeDetails))
                .toList();
    }

    /**
     * 构建单个订单视图对象
     *
     * @param order          订单实体
     * @param details        订单明细列表
     * @param includeDetails 是否包含明细
     * @return 订单视图对象
     */
    private OrderVO build(Orders order, List<OrderDetail> details, boolean includeDetails) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        if (includeDetails) vo.setOrderDetailList(details);
        if (!CollectionUtils.isEmpty(details)) {
            // 拼接订单菜品描述，格式：菜名*数量;
            vo.setOrderDishes(details.stream()
                    .map(detail -> detail.getName() + "*" + detail.getNumber() + ";")
                    .collect(Collectors.joining()));
        }
        return vo;
    }
}