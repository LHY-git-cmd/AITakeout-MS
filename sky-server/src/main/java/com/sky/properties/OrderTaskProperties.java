package com.sky.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 订单定时任务配置属性类
 * 从配置文件中读取sky.order-task前缀下的属性
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "sky.order-task")
public class OrderTaskProperties {

    /**
     * 批量处理订单的最大数量，默认200
     */
    @Min(1)
    @Max(1000)
    private int batchSize = 200;
}