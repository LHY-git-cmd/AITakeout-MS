package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.SearchHistoryDTO;
import com.sky.result.Result;
import com.sky.service.catalog.ProductSearchService;
import com.sky.vo.ProductSearchPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 商品统一搜索及登录用户搜索历史接口。 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class ProductSearchController {
    private final ProductSearchService searchService;

    /**
     * 商品统一搜索接口，支持关键词搜索和游标分页
     *
     * @param keyword 搜索关键词
     * @param cursor  游标分页标记（可选，上一页返回的游标）
     * @param limit   每页数量（可选）
     * @return 商品搜索结果页
     */
    @GetMapping("/products/search")
    public Result<ProductSearchPageVO> search(@RequestParam String keyword,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        return Result.success(searchService.search(keyword, cursor, limit));
    }

    /**
     * 查询当前登录用户的搜索历史关键词列表
     *
     * @return 搜索历史关键词列表
     */
    @GetMapping("/search-history")
    public Result<List<String>> history() {
        return Result.success(searchService.history(BaseContext.getCurrentId()));
    }

    /**
     * 记录一次搜索历史（用户执行搜索后调用）
     *
     * @param dto 包含搜索关键词的数据传输对象
     * @return 操作结果
     */
    @PostMapping("/search-history")
    public Result<String> record(@Valid @RequestBody SearchHistoryDTO dto) {
        searchService.recordHistory(BaseContext.getCurrentId(), dto.getKeyword());
        return Result.success();
    }

    /**
     * 清空当前登录用户的全部搜索历史
     *
     * @return 操作结果
     */
    @DeleteMapping("/search-history")
    public Result<String> clear() {
        searchService.clearHistory(BaseContext.getCurrentId());
        return Result.success();
    }
}