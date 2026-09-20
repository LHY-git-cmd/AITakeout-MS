package com.sky.service.catalog;

import com.sky.entity.UserSearchHistory;
import com.sky.exception.BaseException;
import com.sky.mapper.ProductSearchMapper;
import com.sky.mapper.UserSearchHistoryMapper;
import com.sky.vo.ProductSearchItemVO;
import com.sky.vo.ProductSearchPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** 跨分类商品搜索与登录用户历史管理。 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int HISTORY_LIMIT = 20;

    private final ProductSearchMapper searchMapper;
    private final UserSearchHistoryMapper historyMapper;

    @Value("${sky.search.cursor-secret:sky-search-cursor-development-secret}")
    private String cursorSecret;

    public ProductSearchPageVO search(String keyword, String cursor, Integer requestedLimit) {
        String normalized = normalize(keyword);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        Cursor position = decode(cursor);
        String escaped = escapeLike(normalized);
        List<ProductSearchItemVO> rows = searchMapper.search(normalized, "%" + escaped + "%", escaped + "%",
                position == null ? null : position.score(), position == null ? null : position.type(),
                position == null ? null : position.id(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ProductSearchItemVO> items = hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        String next = hasMore && !items.isEmpty() ? encode(items.get(items.size() - 1)) : null;
        return ProductSearchPageVO.builder().items(items).nextCursor(next).hasMore(hasMore).build();
    }

    @Transactional
    public void recordHistory(long userId, String keyword) {
        String normalized = normalize(keyword);
        String display = keyword.trim().replaceAll("\\s+", " ");
        LocalDateTime now = LocalDateTime.now();
        if (historyMapper.touch(userId, normalized, display, now) == 0) {
            try {
                historyMapper.insert(UserSearchHistory.builder().userId(userId).keyword(display)
                        .normalizedKeyword(normalized).createTime(now).updateTime(now).build());
            } catch (DuplicateKeyException exception) {
                historyMapper.touch(userId, normalized, display, now);
            }
        }
        historyMapper.trimToLimit(userId, HISTORY_LIMIT);
    }

    public List<String> history(long userId) {
        return historyMapper.listRecent(userId, HISTORY_LIMIT).stream().map(UserSearchHistory::getKeyword).toList();
    }

    @Transactional
    public void clearHistory(long userId) {
        historyMapper.clear(userId);
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) throw new BaseException("搜索关键词不能为空");
        String normalized = keyword.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) throw new BaseException("搜索关键词不能超过64个字符");
        return normalized;
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String encode(ProductSearchItemVO item) {
        String payload = item.getRelevanceScore() + "|" + item.getProductType() + "|" + item.getId();
        return base64(payload.getBytes(StandardCharsets.UTF_8)) + "." + base64(sign(payload));
    }

    private Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] segments = cursor.split("\\.", -1);
            if (segments.length != 2) throw new IllegalArgumentException();
            String payload = new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);
            byte[] supplied = Base64.getUrlDecoder().decode(segments[1]);
            if (!MessageDigest.isEqual(sign(payload), supplied)) throw new IllegalArgumentException();
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 3 || !("dish".equals(fields[1]) || "setmeal".equals(fields[1]))) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Integer.parseInt(fields[0]), fields[1], Long.parseLong(fields[2]));
        } catch (RuntimeException exception) {
            throw new BaseException("搜索游标无效，请重新搜索");
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cursorSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("搜索游标签名失败", exception);
        }
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private record Cursor(int score, String type, long id) {}
}
