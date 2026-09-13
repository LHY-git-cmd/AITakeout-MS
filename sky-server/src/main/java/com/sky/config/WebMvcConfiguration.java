package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.interceptor.AdminPermissionInterceptor;
import com.sky.interceptor.AgentInternalServiceInterceptor;
import com.sky.json.JacksonObjectMapper;
import com.sky.properties.CorsProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 注册JWT拦截器、配置CORS跨域、扩展消息转换器及Swagger接口文档
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {
    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;
    private final AdminPermissionInterceptor adminPermissionInterceptor;
    private final AgentInternalServiceInterceptor agentInternalServiceInterceptor;
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
                .excludePathPatterns(
                        "/admin/employee/login",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/favicon.ico"
                );

        registry.addInterceptor(adminPermissionInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/employee/login");

        registry.addInterceptor(agentInternalServiceInterceptor)
                .addPathPatterns("/internal/agent/**");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        "/user/user/login",
                        "/user/user/login/web",
                        "/user/user/register/web",
                        "/user/shop/status",
                        "/user/category/list",
                        "/user/dish/list",
                        "/user/setmeal/list",
                        "/user/setmeal/dish/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/favicon.ico"
                );
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
        return new OpenAPI()
                .info(new Info()
                        .title("苍穹外卖项目接口文档")
                        .version("3.0")
                        .description("苍穹外卖项目管理端和用户端接口文档")
                        .contact(new Contact().name("苍穹外卖开发团队")));
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
        JacksonObjectMapper objectMapper = new JacksonObjectMapper();
        boolean jacksonConverterConfigured = false;
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setObjectMapper(objectMapper);
                jacksonConverterConfigured = true;
            }
        }

        // Keep Springdoc's ByteArrayHttpMessageConverter ahead of Jackson. Its
        // OpenAPI endpoint returns UTF-8 JSON as byte[], which Jackson would
        // otherwise encode as a Base64 JSON string.
        if (!jacksonConverterConfigured) {
            converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
        }
    }

}
