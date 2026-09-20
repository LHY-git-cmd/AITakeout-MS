package com.sky.service.catalog;

import com.sky.mapper.ProductSearchMapper;
import com.sky.mapper.UserSearchHistoryMapper;
import com.sky.vo.ProductSearchItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证搜索稳定游标、批次上限和历史去重。 */
@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {
    @Mock private ProductSearchMapper searchMapper;
    @Mock private UserSearchHistoryMapper historyMapper;
    private ProductSearchService service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchService(searchMapper, historyMapper);
        ReflectionTestUtils.setField(service, "cursorSecret", "test-search-secret-at-least-32-bytes");
    }

    @Test
    void cursorContinuesAfterLastVisibleItemAndCannotBeTampered() {
        when(searchMapper.search(eq("牛肉"), eq("%牛肉%"), eq("牛肉%"), isNull(), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(item(1, "dish", 3), item(2, "dish", 2), item(8, "setmeal", 1)));

        var first = service.search(" 牛肉 ", null, 2);

        assertThat(first.getItems()).extracting(ProductSearchItemVO::getStableKey)
                .containsExactly("dish:1", "dish:2");
        assertThat(first.isHasMore()).isTrue();
        assertThat(first.getNextCursor()).isNotBlank();

        when(searchMapper.search(eq("牛肉"), eq("%牛肉%"), eq("牛肉%"), eq(2), eq("dish"), eq(2L), eq(3)))
                .thenReturn(List.of(item(8, "setmeal", 1)));
        var second = service.search("牛肉", first.getNextCursor(), 2);
        assertThat(second.getItems()).extracting(ProductSearchItemVO::getStableKey).containsExactly("setmeal:8");

        String tampered = first.getNextCursor().substring(0, first.getNextCursor().length() - 1) + "A";
        assertThatThrownBy(() -> service.search("牛肉", tampered, 2))
                .hasMessage("搜索游标无效，请重新搜索");
    }

    @Test
    void repeatedHistoryTouchesExistingRowAndKeepsTwentyItems() {
        when(historyMapper.touch(eq(7L), eq("beef bowl"), eq("Beef Bowl"), any())).thenReturn(1);

        service.recordHistory(7L, "  Beef   Bowl  ");

        verify(historyMapper, never()).insert(any());
        verify(historyMapper).trimToLimit(7L, 20);
    }

    private ProductSearchItemVO item(long id, String type, int score) {
        return ProductSearchItemVO.builder().id(id).productType(type).name("商品" + id)
                .price(BigDecimal.TEN).categoryId(1L).categoryName("分类").relevanceScore(score).build();
    }
}
