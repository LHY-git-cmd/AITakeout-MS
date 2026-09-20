package com.sky.mapper;

import com.sky.vo.ProductSearchItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 跨菜品、套餐分类的统一搜索查询。 */
@Mapper
public interface ProductSearchMapper {
    List<ProductSearchItemVO> search(@Param("keyword") String keyword,
                                     @Param("contains") String contains,
                                     @Param("prefix") String prefix,
                                     @Param("cursorScore") Integer cursorScore,
                                     @Param("cursorType") String cursorType,
                                     @Param("cursorId") Long cursorId,
                                     @Param("limit") int limit);
}
