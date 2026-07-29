package com.sky.config;

import com.sky.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        log.info("初始化RedisTemplate...");
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        //设置Redis的连接工厂对象
        redisTemplate.setConnectionFactory(connectionFactory);

        //设置Redis序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());

        // 使用 GenericJackson2JsonRedisSerializer：自动存储类型信息，反序列化时还原为正确类型
        // 同时传入自定义 JacksonObjectMapper 以支持 LocalDateTime 等 Java 8 时间类型
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(new JacksonObjectMapper());

        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashValueSerializer(serializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("初始化RedisCacheManager...");

        // 使用 GenericJackson2JsonRedisSerializer：自动存储类型信息，反序列化时还原为正确类型
        // 同时传入自定义 JacksonObjectMapper 以支持 LocalDateTime 等 Java 8 时间类型
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(new JacksonObjectMapper());

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))               // 缓存有效期1小时
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))          // 用 Jackson JSON 替代 JDK 二进制
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}