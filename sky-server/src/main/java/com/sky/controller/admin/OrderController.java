package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Tag(name = "订单管理接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 订单条件搜索（支持按订单号、手机号、状态、下单时间等条件筛选）
     * GET /admin/order/conditionSearch
     */
    @GetMapping("/conditionSearch")
    @Operation(summary = "订单搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        return Result.success(orderService.conditionSearch(ordersPageQueryDTO));
    }

    /**
     * 统计各个状态的订单数量（待接单、已接单、派送中）
     * GET /admin/order/statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "各个状态的订单数量统计")
    public Result<OrderStatisticsVO> statistics() {
        return Result.success(orderService.statistics());
    }

    /**
     * 查询订单详情
     * GET /admin/order/details/{id}
     */
    @GetMapping("/details/{id}")
    @Operation(summary = "查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        return Result.success(orderService.details(id));
    }

    /**
     * 接单（将订单状态由"待接单"改为"已接单"）
     * PUT /admin/order/confirm
     */
    @PutMapping("/confirm")
    @Operation(summary = "接单")
    public Result<String> confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }

    /**
     * 拒单（将订单状态改为"已取消"，并记录拒单原因，已支付订单需退款）
     * PUT /admin/order/rejection
     */
    @PutMapping("/rejection")
    @Operation(summary = "拒单")
    public Result<String> rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        orderService.rejection(ordersRejectionDTO);
        return Result.success();
    }

    /**
     * 取消订单（管理端取消订单，需填写取消原因，已支付订单需退款）
     * PUT /admin/order/cancel
     */
    @PutMapping("/cancel")
    @Operation(summary = "取消订单")
    public Result<String> cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) throws Exception {
        orderService.cancel(ordersCancelDTO);
        return Result.success();
    }

    /**
     * 派送订单（将订单状态由"已接单"改为"派送中"）
     * PUT /admin/order/delivery/{id}
     */
    @PutMapping("/delivery/{id}")
    @Operation(summary = "派送订单")
    public Result<String> delivery(@PathVariable Long id) {
        orderService.delivery(id);
        return Result.success();
    }

    /**
     * 完成订单（将订单状态由"派送中"改为"已完成"，记录送达时间）
     * PUT /admin/order/complete/{id}
     */
    @PutMapping("/complete/{id}")
    @Operation(summary = "完成订单")
    public Result<String> complete(@PathVariable Long id) {
        orderService.complete(id);
        return Result.success();
    }
}