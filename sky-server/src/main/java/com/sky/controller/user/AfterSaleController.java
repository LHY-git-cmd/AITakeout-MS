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

    /**
     * 提交售后申请（退款/退货等），支持幂等性防重复提交
     *
     * @param orderId          订单ID
     * @param dto              售后申请信息（类型、原因、金额等）
     * @param idempotencyKey   幂等性键，防止重复提交
     * @return 售后申请详情
     */
    @PostMapping("/orders/{orderId}/after-sales")
    public Result<AfterSaleVO> apply(@PathVariable long orderId, @Valid @RequestBody AfterSaleApplyDTO dto,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(afterSaleService.apply(BaseContext.getCurrentId(), orderId, dto, idempotencyKey));
    }

    /**
     * 查询指定订单的最新一条售后记录
     *
     * @param orderId 订单ID
     * @return 售后详情
     */
    @GetMapping("/orders/{orderId}/after-sales/latest")
    public Result<AfterSaleVO> latest(@PathVariable long orderId) {
        return Result.success(afterSaleService.getForUser(BaseContext.getCurrentId(), orderId));
    }

    /**
     * 分页查询当前用户的售后列表
     *
     * @param beforeId 游标分页的上一页最后一条记录ID（可选）
     * @param limit    每页数量，默认20
     * @return 售后列表
     */
    @GetMapping("/after-sales")
    public Result<List<AfterSaleVO>> list(@RequestParam(required = false) Long beforeId,
                                          @RequestParam(defaultValue = "20") int limit) {
        return Result.success(afterSaleService.listForUser(BaseContext.getCurrentId(), beforeId, limit));
    }

    /**
     * 根据退款单号查询退款详情
     *
     * @param refundNo 退款单号
     * @return 退款详情视图
     */
    @GetMapping("/refunds/{refundNo}")
    public Result<RefundView> refund(@PathVariable String refundNo) {
        return Result.success(refundService.query(BaseContext.getCurrentId(), refundNo));
    }

    /**
     * 查询订单的时间轴（包含下单、支付、制作、配送等各状态节点）
     *
     * @param orderId 订单ID
     * @return 订单时间轴节点列表
     */
    @GetMapping("/orders/{orderId}/timeline")
    public Result<List<OrderTimelineVO>> timeline(@PathVariable long orderId) {
        return Result.success(timelineService.timeline(BaseContext.getCurrentId(), orderId));
    }
}