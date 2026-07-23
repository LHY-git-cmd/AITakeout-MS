package com.sky.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    public void hset(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Object hget(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public Map<Object, Object> hgetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public void hdel(String key, Object... hashKeys) {
        redisTemplate.opsForHash().delete(key, hashKeys);
    }

    public Boolean hExists(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    public Long hLen(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    public void lpush(String key, Object... values) {
        redisTemplate.opsForList().leftPushAll(key, values);
    }

    public void rpush(String key, Object... values) {
        redisTemplate.opsForList().rightPushAll(key, values);
    }

    public Object lpop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public Object rpop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    public List<Object> lrange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public Long llen(String key) {
        return redisTemplate.opsForList().size(key);
    }

    public void sadd(String key, Object... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    public Set<Object> smembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Boolean sismember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Long srem(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    public Long scard(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    public void zadd(String key, Object value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<Object> zrange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    public Long zrem(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    public Long zcard(String key) {
        return redisTemplate.opsForZSet().size(key);
    }
}