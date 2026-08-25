package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.json.JacksonObjectMapper;
import com.sky.properties.CorsProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC配置类
 * 注册JWT拦截器、配置CORS跨域、扩展消息转换器及Knife4j接口文档
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {
    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;
    private final CorsProperties corsProperties;

    /**
     * 注册自定义拦截器
     * 管理端拦截/admin/**路径（排除登录接口），用户端拦截/user/**路径（排除登录等公开接口）
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/employee/login");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login")
                .excludePathPatterns("/user/user/login/web")
                .excludePathPatterns("/user/user/register/web")
                .excludePathPatterns("/user/shop/status")
                .excludePathPatterns("/user/category/list")
                .excludePathPatterns("/user/dish/list")
                .excludePathPatterns("/user/setmeal/list")
                .excludePathPatterns("/user/setmeal/dish/**");
    }

    /**
     * 配置全局CORS跨域
     *
     * @param registry CORS注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置OpenAPI接口文档基本信息
     *
     * @return OpenAPI实例
     */
    @Bean
    public OpenAPI skyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("苍穹外卖项目接口文档")
                .version("3.0")
                .description("苍穹外卖项目管理端和用户端接口文档"));
    }

    /**
     * 管理端API分组
     *
     * @return GroupedOpenApi实例
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/admin/**")
                .build();
    }

    /**
     * 用户端API分组
     *
     * @return GroupedOpenApi实例
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("user")
                .pathsToMatch("/user/**")
                .build();
    }

    /**
     * 扩展Spring MVC消息转换器
     * 使用自定义的JacksonObjectMapper支持Java 8时间类型
     *
     * @param converters 消息转换器列表
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("扩展Spring MVC消息转换器...");
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new JacksonObjectMapper());
        converters.add(0, converter);
    }

}
