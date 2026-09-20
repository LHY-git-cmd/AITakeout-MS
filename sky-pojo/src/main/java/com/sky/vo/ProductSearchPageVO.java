package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "产品搜索页面视图对象")
public class ProductSearchPageVO {
    @Schema(description = "项目列表")
    private List<ProductSearchItemVO> items;
    @Schema(description = "下一页的游标")
    private String nextCursor;
    @Schema(description = "是否还有更多")
    private boolean hasMore;
}