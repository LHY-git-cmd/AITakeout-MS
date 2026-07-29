package com.sky.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
        GenericJackson2JsonRedisSerializer serializer = createJsonSerializer();

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
        GenericJackson2JsonRedisSerializer serializer = createJsonSerializer();

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

    /**
     * 创建携带Java类型信息的JSON序列化器，保证缓存命中时可以还原为原始对象，
     * 避免Result、Setmeal等对象被反序列化为LinkedHashMap。
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        JacksonObjectMapper objectMapper = new JacksonObjectMapper();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
