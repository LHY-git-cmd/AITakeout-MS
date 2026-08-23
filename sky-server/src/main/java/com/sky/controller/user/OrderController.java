package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器（用户端）
 * 提供用户下单、支付、历史订单查询、取消订单、再来一单、催单等功能
 */
@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Tag(name = "C端订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO 下单信息
     * @return 下单结果（含订单ID和订单号）
     */
    @PostMapping("/submit")
    @Operation(summary = "用户下单")
    public Result<OrderSubmitVO> submit(@Valid @RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 支付信息
     * @return 支付预下单结果
     */
    @PutMapping("/payment")
    @Operation(summary = "订单支付")
    public Result<OrderPaymentVO> payment(@Valid @RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 查询历史订单（分页）
     *
     * @param page     页码
     * @param pageSize 每页数量
     * @param status   订单状态
     * @return 分页结果
     */
    @GetMapping("/historyOrders")
    @Operation(summary = "历史订单查询")
    public Result<PageResult> historyOrders(int page, int pageSize, Integer status) {
        return Result.success(orderService.pageQuery4User(page, pageSize, status));
    }

    /**
     * 查询订单详情
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @GetMapping("/orderDetail/{id}")
    @Operation(summary = "查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        return Result.success(orderService.detailsForUser(id));
    }

    /**
     * 用户取消订单
     *
     * @param id 订单ID
     * @return 操作结果
     */
    @PutMapping("/cancel/{id}")
    @Operation(summary = "取消订单")
    public Result<String> cancel(@PathVariable Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单（将历史订单中的菜品加入购物车）
     *
     * @param id 订单ID
     * @return 操作结果
     */
    @PostMapping("/repetition/{id}")
    @Operation(summary = "再来一单")
    public Result<String> repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 客户催单（向商家发送催单提醒）
     *
     * @param id 订单ID
     * @return 操作结果
     */
    @GetMapping("/reminder/{id}")
    @Operation(summary = "客户催单")
    public Result<String> reminder(@PathVariable Long id) {
        orderService.reminder(id);
        return Result.success();
    }

}