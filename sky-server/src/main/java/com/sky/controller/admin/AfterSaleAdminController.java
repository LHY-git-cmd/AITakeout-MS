package com.sky.controller.admin;

import com.sky.context.BaseContext;
import com.sky.annotation.RequireAdminPermission;
import com.sky.enumeration.AdminPermission;
import com.sky.dto.AfterSaleReviewDTO;
import com.sky.result.Result;
import com.sky.service.aftersale.AfterSaleService;
import com.sky.service.payment.RefundApplicationService;
import com.sky.service.payment.model.RefundModels.RefundView;
import com.sky.vo.AfterSaleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理端售后审核和异常退款处理接口。 */
@RestController
@RequestMapping("/admin/after-sales")
@RequiredArgsConstructor
public class AfterSaleAdminController {
    private final AfterSaleService afterSaleService;
    private final RefundApplicationService refundService;

    @GetMapping
    @RequireAdminPermission(AdminPermission.ORDER_READ)
    public Result<List<AfterSaleVO>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long beforeId,
                                          @RequestParam(defaultValue = "20") int limit) {
        return Result.success(afterSaleService.listForAdmin(status, beforeId, limit));
    }

    @GetMapping("/{id}")
    @RequireAdminPermission(AdminPermission.ORDER_READ)
    public Result<AfterSaleVO> detail(@PathVariable long id) {
        return Result.success(afterSaleService.getForAdmin(id));
    }

    @PutMapping("/{id}/review")
    @RequireAdminPermission(AdminPermission.ORDER_STATUS_WRITE)
    public Result<AfterSaleVO> review(@PathVariable long id, @Valid @RequestBody AfterSaleReviewDTO dto) {
        return Result.success(afterSaleService.review(BaseContext.getCurrentId(), id, dto));
    }

    @PostMapping("/refunds/{refundNo}/retry")
    @RequireAdminPermission(AdminPermission.ORDER_STATUS_WRITE)
    public Result<RefundView> retry(@PathVariable String refundNo) {
        return Result.success(refundService.retry(refundNo));
    }
}
