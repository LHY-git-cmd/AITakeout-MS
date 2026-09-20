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

    @PostMapping("/orders/{id}/payments")
    public Result<PaymentView> create(@PathVariable Long id,
                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return Result.success(paymentService.create(BaseContext.getCurrentId(), id, idempotencyKey));
    }

    @GetMapping("/payments/{paymentNo}")
    public Result<PaymentView> query(@PathVariable String paymentNo) {
        return Result.success(paymentService.query(BaseContext.getCurrentId(), paymentNo));
    }
}
