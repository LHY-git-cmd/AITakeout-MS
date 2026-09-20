package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.AfterSaleApplyDTO;
import com.sky.result.Result;
import com.sky.service.aftersale.AfterSaleService;
import com.sky.service.order.OrderTimelineService;
import com.sky.service.payment.RefundApplicationService;
import com.sky.service.payment.model.RefundModels.RefundView;
import com.sky.vo.AfterSaleVO;
import com.sky.vo.OrderTimelineVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 用户取消、售后、退款及订单时间轴接口。 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class AfterSaleController {
    private final AfterSaleService afterSaleService;
    private final RefundApplicationService refundService;
    private final OrderTimelineService timelineService;

    @PostMapping("/orders/{orderId}/after-sales")
    public Result<AfterSaleVO> apply(@PathVariable long orderId, @Valid @RequestBody AfterSaleApplyDTO dto,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(afterSaleService.apply(BaseContext.getCurrentId(), orderId, dto, idempotencyKey));
    }

    @GetMapping("/orders/{orderId}/after-sales/latest")
    public Result<AfterSaleVO> latest(@PathVariable long orderId) {
        return Result.success(afterSaleService.getForUser(BaseContext.getCurrentId(), orderId));
    }

    @GetMapping("/after-sales")
    public Result<List<AfterSaleVO>> list(@RequestParam(required = false) Long beforeId,
                                          @RequestParam(defaultValue = "20") int limit) {
        return Result.success(afterSaleService.listForUser(BaseContext.getCurrentId(), beforeId, limit));
    }

    @GetMapping("/refunds/{refundNo}")
    public Result<RefundView> refund(@PathVariable String refundNo) {
        return Result.success(refundService.query(BaseContext.getCurrentId(), refundNo));
    }

    @GetMapping("/orders/{orderId}/timeline")
    public Result<List<OrderTimelineVO>> timeline(@PathVariable long orderId) {
        return Result.success(timelineService.timeline(BaseContext.getCurrentId(), orderId));
    }
}
