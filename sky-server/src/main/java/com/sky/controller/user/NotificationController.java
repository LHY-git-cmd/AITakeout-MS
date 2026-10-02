package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.result.Result;
import com.sky.service.notification.NotificationService;
import com.sky.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 用户通知中心接口。 */
@RestController
@RequestMapping("/user/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    /**
     * 查询当前用户的通知列表，支持游标分页和按时间范围筛选
     *
     * @param beforeId 游标分页的上一页最后一条记录ID（可选）
     * @param since    查询该时间点之后的通知（可选）
     * @param limit    每页数量，默认20
     * @return 通知列表
     */
    @GetMapping
    public Result<List<NotificationVO>> list(@RequestParam(required = false) Long beforeId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime since,
                                             @RequestParam(defaultValue = "20") int limit) {
        return Result.success(notificationService.list(BaseContext.getCurrentId(), beforeId, since, limit));
    }

    /**
     * 查询当前用户的未读通知数量
     *
     * @return 未读数量（包装在count字段中）
     */
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", notificationService.unreadCount(BaseContext.getCurrentId())));
    }

    /**
     * 将指定ID的通知标记为已读
     *
     * @param id 通知ID
     * @return 操作结果
     */
    @PutMapping("/{id}/read")
    public Result<String> markRead(@PathVariable long id) {
        notificationService.markRead(BaseContext.getCurrentId(), id);
        return Result.success();
    }

    /**
     * 将当前用户的所有通知一次性标记为已读
     *
     * @return 操作结果
     */
    @PutMapping("/read-all")
    public Result<String> markAllRead() {
        notificationService.markAllRead(BaseContext.getCurrentId());
        return Result.success();
    }
}