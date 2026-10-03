package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.result.Result;
import com.sky.service.payment.PaymentApplicationService;
import com.sky.service.payment.model.PaymentModels.PaymentView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户端支付创建与服务端可信状态查询接口。 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserPaymentController {
    private final PaymentApplicationService paymentService;

    /**
     * 为指定订单创建支付单，支持幂等性防重复创建
     *
     * @param id             订单ID
     * @param idempotencyKey 幂等性键，防止重复创建支付单
     * @return 支付单信息（含支付参数）
     */
    @PostMapping("/orders/{id}/payments")
    public Result<PaymentView> create(@PathVariable Long id,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return Result.success(paymentService.create(BaseContext.getCurrentId(), id, idempotencyKey));
    }

    /**
     * 查询支付单的可信状态（以服务端记录为准，而非客户端状态）
     *
     * @param paymentNo 支付单号
     * @return 支付单详情（含支付状态）
     */
    @GetMapping("/payments/{paymentNo}")
    public Result<PaymentView> query(@PathVariable String paymentNo) {
        return Result.success(paymentService.query(BaseContext.getCurrentId(), paymentNo));
    }
}