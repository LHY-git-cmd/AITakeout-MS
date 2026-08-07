package com.sky.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 依赖外部Redis的集成测试。默认mvn test不会执行*IT测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SpringDataRedisIT {

    private static final String KEY_PREFIX = "test:upgrade:" + UUID.randomUUID() + ":";
    private static final String CITY_KEY = KEY_PREFIX + "city";
    private static final String CODE_KEY = KEY_PREFIX + "code";
    private static final String LOCK_KEY = KEY_PREFIX + "lock";
    private static final String HASH_KEY = KEY_PREFIX + "hash";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(List.of(CITY_KEY, CODE_KEY, LOCK_KEY, HASH_KEY));
    }

    @Test
    public void testRedisTemplate() {
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        ListOperations<String, Object> listOperations = redisTemplate.opsForList();
        SetOperations<String, Object> setOperations = redisTemplate.opsForSet();
        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();

        assertNotNull(valueOperations);
        assertNotNull(hashOperations);
        assertNotNull(listOperations);
        assertNotNull(setOperations);
        assertNotNull(zSetOperations);
    }

    @Test
    public void testString() {
        redisTemplate.opsForValue().set(CITY_KEY, "北京");
        redisTemplate.opsForValue().get(CITY_KEY);
        redisTemplate.opsForValue().set(CODE_KEY, "123", 3, TimeUnit.MINUTES);
        redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, 1);
        redisTemplate.opsForValue().getAndSet(LOCK_KEY, 2);
    }

    @Test
    public void testHash() {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        hashOperations.put(HASH_KEY, "name", "zhangsan");
        hashOperations.put(HASH_KEY, "age", "22");

        hashOperations.get(HASH_KEY, "name");
        Set<Object> keys = hashOperations.keys(HASH_KEY);
        List<Object> values = hashOperations.values(HASH_KEY);

        assertNotNull(keys);
        assertNotNull(values);
        hashOperations.delete(HASH_KEY, "age");
    }
}
