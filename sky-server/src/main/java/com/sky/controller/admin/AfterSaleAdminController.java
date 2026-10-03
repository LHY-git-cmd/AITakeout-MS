package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.dto.AfterSaleReviewDTO;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.aftersale.AfterSaleService;
import com.sky.service.payment.RefundApplicationService;
import com.sky.service.payment.model.RefundModels.RefundView;
import com.sky.vo.AfterSaleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端售后服务管理接口
 * <p>
 * 提供对售后申请的查询、审核以及异常退款的重试功能。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/after-sales")
@RequiredArgsConstructor
@Tag(name = "售后服务管理接口")
public class AfterSaleAdminController {
    private final AfterSaleService afterSaleService;
    private final RefundApplicationService refundService;

    /**
     * 根据条件查询售后申请列表。
     *
     * @param status   售后状态。
     * @param beforeId 分页查询参数，查询此ID之前的记录。
     * @param limit    每页数量。
     * @return 售后申请列表。
     */
    @GetMapping
    @Operation(summary = "查询售后申请列表")
    @RequireAdminPermission(AdminPermission.ORDER_READ)
    public Result<List<AfterSaleVO>> list(
            @Parameter(description = "售后状态") @RequestParam(required = false) String status,
            @Parameter(description = "分页查询锚点") @RequestParam(required = false) Long beforeId,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int limit) {
        log.info("查询售后申请列表: status={}, beforeId={}, limit={}", status, beforeId, limit);
        return Result.success(afterSaleService.listForAdmin(status, beforeId, limit));
    }

    /**
     * 获取指定ID的售后申请详情。
     *
     * @param id 售后申请ID。
     * @return 售后申请详情。
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取售后申请详情")
    @RequireAdminPermission(AdminPermission.ORDER_READ)
    public Result<AfterSaleVO> detail(@Parameter(description = "售后申请ID") @PathVariable long id) {
        log.info("获取售后申请详情: id={}", id);
        return Result.success(afterSaleService.getForAdmin(id));
    }

    /**
     * 审核售后申请。
     *
     * @param id  售后申请ID。
     * @param dto 包含审核信息的DTO。
     * @return 审核后的售后申请详情。
     */
    @PutMapping("/{id}/review")
    @Operation(summary = "审核售后申请")
    @RequireAdminPermission(AdminPermission.ORDER_STATUS_WRITE)
    public Result<AfterSaleVO> review(
            @Parameter(description = "售后申请ID") @PathVariable long id,
            @Valid @RequestBody AfterSaleReviewDTO dto) {
        log.info("审核售后申请: id={}, review={}", id, dto);
        return Result.success(afterSaleService.review(BaseContext.getCurrentId(), id, dto));
    }

    /**
     * 重试失败的退款申请。
     *
     * @param refundNo 退款单号。
     * @return 退款视图。
     */
    @PostMapping("/refunds/{refundNo}/retry")
    @Operation(summary = "重试退款")
    @RequireAdminPermission(AdminPermission.ORDER_STATUS_WRITE)
    public Result<RefundView> retry(@Parameter(description = "退款单号") @PathVariable String refundNo) {
        log.info("重试退款: refundNo={}", refundNo);
        return Result.success(refundService.retry(refundNo));
    }
}