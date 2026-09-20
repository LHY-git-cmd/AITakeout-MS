package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 单页连续搜索的游标批次。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchPageVO {
    private List<ProductSearchItemVO> items;
    private String nextCursor;
    private boolean hasMore;
}
