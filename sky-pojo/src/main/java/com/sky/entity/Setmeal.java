package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Setmeal implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;                    // 主键
    private Long categoryId;            // 分类id
    private String name;                // 套餐名称
    private BigDecimal price;           // 套餐价格
    private Integer status;             // 状态 0:停用, 1:启用
    private String description;         // 描述信息
    private String image;               // 图片
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
    private Long createUser;            // 创建人
    private Long updateUser;            // 修改人
}