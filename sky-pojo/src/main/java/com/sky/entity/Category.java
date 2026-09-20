package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;                // 主键
    private Integer type;           // 类型: 1菜品分类 2套餐分类
    private String name;            // 分类名称
    private Integer sort;           // 顺序
    private Integer status;         // 状态 0:禁用, 1:启用
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
    private Long createUser;        // 创建人
    private Long updateUser;        // 修改人
}