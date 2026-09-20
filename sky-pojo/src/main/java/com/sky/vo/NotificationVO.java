package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Schema(description = "通知中心列表项视图对象")
public record NotificationVO(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "事件ID") String eventId,
        @Schema(description = "类型") String type,
        @Schema(description = "标题") String title,
        @Schema(description = "内容") String content,
        @Schema(description = "订单ID") Long orderId,
        @Schema(description = "是否已读") boolean read,
        @Schema(description = "创建时间") LocalDateTime createTime) implements Serializable {
}