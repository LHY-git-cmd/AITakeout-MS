package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.result.Result;
import com.sky.service.notification.NotificationService;
import com.sky.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public Result<List<NotificationVO>> list(@RequestParam(required = false) Long beforeId,
                                             @RequestParam(required = false) LocalDateTime since,
                                             @RequestParam(defaultValue = "20") int limit) {
        return Result.success(notificationService.list(BaseContext.getCurrentId(), beforeId, since, limit));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", notificationService.unreadCount(BaseContext.getCurrentId())));
    }

    @PutMapping("/{id}/read")
    public Result<String> markRead(@PathVariable long id) {
        notificationService.markRead(BaseContext.getCurrentId(), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<String> markAllRead() {
        notificationService.markAllRead(BaseContext.getCurrentId());
        return Result.success();
    }
}
