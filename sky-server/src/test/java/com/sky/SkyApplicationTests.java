package com.sky;

import com.sky.vo.DishVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SkyApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void contextLoads() {
        assertNotNull(restTemplate);
    }

    @Test
    void openApiAndSwaggerUiEndpointsAreAvailable() {
        ResponseEntity<String> apiDocs = restTemplate.getForEntity("/v3/api-docs", String.class);
        ResponseEntity<String> swaggerUi = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertEquals(HttpStatus.OK, apiDocs.getStatusCode());
        assertNotNull(apiDocs.getBody());
        assertTrue(apiDocs.getBody().contains("\"openapi\""));
        assertEquals(HttpStatus.OK, swaggerUi.getStatusCode());
    }

    @Test
    void protectedAdminEndpointRejectsMissingToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/admin/workspace/businessData", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisJsonSerializerPreservesObjectType() {
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        DishVO dish = DishVO.builder().id(1L).name("测试菜品").build();

        byte[] bytes = serializer.serialize(dish);
        Object restored = serializer.deserialize(bytes);

        assertNotNull(bytes);
        assertNotNull(restored);
        assertEquals(DishVO.class, restored.getClass());
        assertEquals(dish.getId(), ((DishVO) restored).getId());
        assertEquals(dish.getName(), ((DishVO) restored).getName());
    }
}
