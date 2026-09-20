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

    @GetMapping("/products/search")
    public Result<ProductSearchPageVO> search(@RequestParam String keyword,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        return Result.success(searchService.search(keyword, cursor, limit));
    }

    @GetMapping("/search-history")
    public Result<List<String>> history() {
        return Result.success(searchService.history(BaseContext.getCurrentId()));
    }

    @PostMapping("/search-history")
    public Result<String> record(@Valid @RequestBody SearchHistoryDTO dto) {
        searchService.recordHistory(BaseContext.getCurrentId(), dto.getKeyword());
        return Result.success();
    }

    @DeleteMapping("/search-history")
    public Result<String> clear() {
        searchService.clearHistory(BaseContext.getCurrentId());
        return Result.success();
    }
}
