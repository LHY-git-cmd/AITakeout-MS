package com.sky.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类
 * 封装Redis常用操作，包括String、Hash、List、Set、ZSet等数据类型的操作
 */
@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 普通缓存存储
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 普通缓存存储并设置过期时间
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 获取普通缓存
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     *
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 判断缓存是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public Boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 获取缓存的过期时间
     *
     * @param key 键
     * @return 过期时间（秒）
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    /**
     * 设置缓存过期时间
     *
     * @param key     键
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return 是否设置成功
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 递增操作
     *
     * @param key   键
     * @param delta 递增量
     * @return 递增后的值
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 递减操作
     *
     * @param key   键
     * @param delta 递减量
     * @return 递减后的值
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * Hash结构添加数据
     *
     * @param key     键
     * @param hashKey Hash键
     * @param value   值
     */
    public void hset(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * Hash结构获取数据
     *
     * @param key     键
     * @param hashKey Hash键
     * @return 值
     */
    public Object hget(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * Hash结构获取所有数据
     *
     * @param key 键
     * @return 所有键值对
     */
    public Map<Object, Object> hgetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * Hash结构删除数据
     *
     * @param key      键
     * @param hashKeys Hash键数组
     */
    public void hdel(String key, Object... hashKeys) {
        redisTemplate.opsForHash().delete(key, hashKeys);
    }

    /**
     * Hash结构判断键是否存在
     *
     * @param key     键
     * @param hashKey Hash键
     * @return 是否存在
     */
    public Boolean hExists(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * Hash结构获取字段数量
     *
     * @param key 键
     * @return 字段数量
     */
    public Long hLen(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    /**
     * List结构从左侧推入数据
     *
     * @param key    键
     * @param values 值数组
     */
    public void lpush(String key, Object... values) {
        redisTemplate.opsForList().leftPushAll(key, values);
    }

    /**
     * List结构从右侧推入数据
     *
     * @param key    键
     * @param values 值数组
     */
    public void rpush(String key, Object... values) {
        redisTemplate.opsForList().rightPushAll(key, values);
    }

    /**
     * List结构从左侧弹出数据
     *
     * @param key 键
     * @return 值
     */
    public Object lpop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * List结构从右侧弹出数据
     *
     * @param key 键
     * @return 值
     */
    public Object rpop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * List结构获取指定范围的数据
     *
     * @param key   键
     * @param start 开始位置
     * @param end   结束位置
     * @return 值列表
     */
    public List<Object> lrange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * List结构获取长度
     *
     * @param key 键
     * @return 长度
     */
    public Long llen(String key) {
        return redisTemplate.opsForList().size(key);
    }

    /**
     * Set结构添加数据
     *
     * @param key    键
     * @param values 值数组
     */
    public void sadd(String key, Object... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    /**
     * Set结构获取所有成员
     *
     * @param key 键
     * @return 成员集合
     */
    public Set<Object> smembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * Set结构判断成员是否存在
     *
     * @param key   键
     * @param value 值
     * @return 是否存在
     */
    public Boolean sismember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * Set结构删除成员
     *
     * @param key    键
     * @param values 值数组
     * @return 删除的数量
     */
    public Long srem(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    /**
     * Set结构获取成员数量
     *
     * @param key 键
     * @return 成员数量
     */
    public Long scard(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    /**
     * ZSet结构添加数据
     *
     * @param key   键
     * @param value 值
     * @param score 分数
     */
    public void zadd(String key, Object value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    /**
     * ZSet结构获取指定范围的数据
     *
     * @param key   键
     * @param start 开始位置
     * @param end   结束位置
     * @return 值集合
     */
    public Set<Object> zrange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * ZSet结构删除成员
     *
     * @param key    键
     * @param values 值数组
     * @return 删除的数量
     */
    public Long zrem(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    /**
     * ZSet结构获取成员数量
     *
     * @param key 键
     * @return 成员数量
     */
    public Long zcard(String key) {
        return redisTemplate.opsForZSet().size(key);
    }
}