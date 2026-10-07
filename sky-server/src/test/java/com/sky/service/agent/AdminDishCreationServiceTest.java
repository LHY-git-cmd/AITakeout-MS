package com.sky.service.agent;

import com.fasterxml.jackson.databind.*;
import com.sky.entity.Category;
import com.sky.dto.DishNutritionDTO;
import com.sky.mapper.*;
import com.sky.service.diet.DietRecommendationService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 在真实H2事务中验证批次回滚、确认状态与幂等结果；不连接部署数据库。 */
@SpringJUnitConfig(AdminDishCreationServiceTest.Config.class)
class AdminDishCreationServiceTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:dish_creation;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean CategoryMapper categories() { return mock(CategoryMapper.class); }
        @Bean DishMapper dishes() { return mock(DishMapper.class); }
        @Bean DishFlavorMapper flavors() { return mock(DishFlavorMapper.class); }
        @Bean AgentToolConfirmationMapper confirmations() { return mock(AgentToolConfirmationMapper.class); }
        @Bean DietRecommendationService nutrition() { return mock(DietRecommendationService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean AdminDishCreationService service(CategoryMapper c, DishMapper d, DishFlavorMapper f,
                AgentToolConfirmationMapper a, DietRecommendationService n, ObjectMapper o, Validator v) {
            return new AdminDishCreationService(c, d, f, a, n, o, v);
        }
    }

    @Autowired AdminDishCreationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired CategoryMapper categories;
    @Autowired DishMapper dishes;
    @Autowired AgentToolConfirmationMapper confirmations;
    @Autowired DietRecommendationService nutrition;
    @Autowired ObjectMapper json;
    private final AtomicLong ids = new AtomicLong();

    @BeforeEach void setup() {
        reset(categories, dishes, confirmations, nutrition);
        jdbc.execute("drop table if exists dishes");
        jdbc.execute("drop table if exists nutrition");
        jdbc.execute("drop table if exists confirmations");
        jdbc.execute("create table dishes(id bigint primary key, name varchar(64) unique, status int, image varchar(255))");
        jdbc.execute("create table nutrition(dish_id bigint)");
        jdbc.execute("create table confirmations(id varchar(32) primary key, status varchar(32), result text)");
        jdbc.update("insert into confirmations values('confirm-1','EXECUTING',null)");
        Category category = Category.builder().id(1L).name("主食").type(1).status(1).build();
        when(categories.getById(1L)).thenReturn(category);
        when(categories.lockById(1L)).thenReturn(category);
        when(confirmations.lockStatus(anyString())).thenAnswer(i -> jdbc.queryForObject(
                "select status from confirmations where id=? for update", String.class, i.getArgument(0, String.class)));
        when(confirmations.getResult(anyString())).thenAnswer(i -> jdbc.queryForObject(
                "select result from confirmations where id=?", String.class, i.getArgument(0, String.class)));
        when(confirmations.completeDishCreation(anyString(), anyString())).thenAnswer(i -> jdbc.update(
                "update confirmations set result=?, status='EXECUTED' where id=? and status='EXECUTING'",
                i.getArgument(1, String.class), i.getArgument(0, String.class)));
        when(dishes.countByName(anyString())).thenAnswer(i -> jdbc.queryForObject(
                "select count(*) from dishes where name=?", Integer.class, i.getArgument(0, String.class)));
        doAnswer(i -> {
            com.sky.entity.Dish dish = i.getArgument(0); dish.setId(ids.incrementAndGet());
            jdbc.update("insert into dishes values(?,?,?,?)", dish.getId(), dish.getName(), dish.getStatus(), dish.getImage());
            return null;
        }).when(dishes).insert(any());
        doAnswer(i -> { jdbc.update("insert into nutrition values(?)", (Long)i.getArgument(0)); return null; })
                .when(nutrition).saveNutrition(anyLong(), any(), anyLong());
    }

    private JsonNode batch() throws Exception {
        var request = json.createObjectNode(); request.put("test_data", true);
        var list = request.putArray("dishes");
        for (int i=0; i<3; i++) list.add(json.readTree("""
                {"name":"测试菜品%s","category_id":1,"price":"18.50","description":"测试配方",
                 "nutrition":{"serving_size_g":200,"energy_kcal":250,"protein_g":10,"fat_g":8,
                 "carbohydrate_g":30,"dietary_fiber_g":3,"sugar_g":2,"sodium_mg":300,
                 "source_type":"SIMULATED","source_reference":"测试",
                 "ingredients":[{"code":"RICE","name":"米饭","amount_g":150}],
                 "allergens":[{"code":"SOY","status":"UNKNOWN","source_reference":"测试"}]}}
                """.formatted(i)));
        return request;
    }

    private JsonNode createBatch() throws Exception {
        var args = batch();
        String version = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(service.categoryVersion(args).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return service.create("confirm-1", args, 9L, version);
    }

    @Test void savesThreeOffSaleDishesAndReplaysSameResult() throws Exception {
        var result = createBatch();
        assertThat(result.path("dishes").size()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from dishes where status=0 and image=''", Integer.class)).isEqualTo(3);
        assertThat(createBatch()).isEqualTo(json.readTree(result.toString()));
        assertThat(jdbc.queryForObject("select count(*) from dishes", Integer.class)).isEqualTo(3);
        verify(nutrition, times(3)).saveNutrition(anyLong(), any(DishNutritionDTO.class), eq(9L));
    }

    @Test void nutritionFailureRollsBackEntireBatchAndResult() throws Exception {
        doAnswer(i -> {
            jdbc.update("insert into nutrition values(?)", (Long)i.getArgument(0));
            if (jdbc.queryForObject("select count(*) from nutrition", Integer.class) == 2)
                throw new IllegalStateException("模拟第二道营养保存失败");
            return null;
        }).when(nutrition).saveNutrition(anyLong(), any(), anyLong());
        assertThatThrownBy(() -> createBatch()).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from dishes", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from nutrition", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select status from confirmations", String.class)).isEqualTo("EXECUTING");
        assertThat(jdbc.queryForObject("select result from confirmations", String.class)).isNull();
    }

    @Test void rejectsUnconfirmedOperationWithoutWriting() throws Exception {
        jdbc.update("update confirmations set status='PENDING'");
        assertThatThrownBy(() -> createBatch()).hasMessageContaining("尚未确认");
        verify(dishes, never()).insert(any());
    }

    @Test void validatesCategoryMoneyUnknownFieldsAndNestedData() throws Exception {
        var args = batch();
        when(categories.getById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.validate(args)).hasMessageContaining("分类");
        when(categories.getById(1L)).thenReturn(Category.builder().id(1L).name("主食").type(1).status(1).build());
        ((com.fasterxml.jackson.databind.node.ObjectNode)args.path("dishes").get(0)).put("price", "1.001");
        assertThatThrownBy(() -> service.validate(args)).isInstanceOf(IllegalArgumentException.class);
        var injected = batch();
        ((com.fasterxml.jackson.databind.node.ObjectNode)injected.path("dishes").get(0)).put("status", 1);
        assertThatThrownBy(() -> service.validate(injected)).hasMessageContaining("格式无效");
        var nested = batch();
        ((com.fasterxml.jackson.databind.node.ObjectNode)nested.path("dishes").get(0).path("nutrition")
                .path("ingredients").get(0)).put("code", "");
        assertThatThrownBy(() -> service.validate(nested)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateExistingNameRollsBackEarlierDishInBatch() throws Exception {
        jdbc.update("insert into dishes values(900,'测试菜品1',0,'')");
        assertThatThrownBy(this::createBatch).hasMessageContaining("名称已存在");
        assertThat(jdbc.queryForObject("select count(*) from dishes", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from nutrition", Integer.class)).isZero();
    }

    @Test void changedCategoryBetweenConfirmationAndLockRejectsCreation() throws Exception {
        when(categories.lockById(1L)).thenAnswer(i -> {
            var changed = Category.builder().id(1L).name("更名的分类").type(1).status(1).build();
            when(categories.getById(1L)).thenReturn(changed);
            return changed;
        });
        assertThatThrownBy(this::createBatch).hasMessageContaining("分类已变化");
        verify(dishes, never()).insert(any());
    }

    @Test void resultPersistenceFailureRollsBackCreatedDishes() throws Exception {
        when(confirmations.completeDishCreation(anyString(), anyString())).thenReturn(0);
        assertThatThrownBy(this::createBatch).hasMessageContaining("无法保存");
        assertThat(jdbc.queryForObject("select count(*) from dishes", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from nutrition", Integer.class)).isZero();
    }
}
