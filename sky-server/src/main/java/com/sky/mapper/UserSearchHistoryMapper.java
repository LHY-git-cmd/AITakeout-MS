package com.sky.mapper;

import com.sky.entity.UserSearchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 用户搜索历史数据访问。 */
@Mapper
public interface UserSearchHistoryMapper {
    int insert(UserSearchHistory history);
    int touch(@Param("userId") long userId, @Param("normalizedKeyword") String normalizedKeyword,
              @Param("keyword") String keyword, @Param("updateTime") LocalDateTime updateTime);
    List<UserSearchHistory> listRecent(@Param("userId") long userId, @Param("limit") int limit);
    int trimToLimit(@Param("userId") long userId, @Param("limit") int limit);
    int clear(long userId);
}
