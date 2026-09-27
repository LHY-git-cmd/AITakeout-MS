package com.sky.agent;

import com.sky.controller.admin.AgentController;
import com.sky.controller.user.UserAgentController;
import com.sky.service.AdminAgentService;
import com.sky.service.UserAgentService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 静态验证管理端与用户端不会因Mapper或Controller注入重新串域。 */
class AgentDomainBoundaryTest {
    private static final List<String> MAPPERS = List.of(
            "AgentEventMapper.xml", "AgentMessageMapper.xml", "AgentSessionMapper.xml",
            "AgentSessionSummaryMapper.xml", "AgentTaskMapper.xml");

    @Test
    void mapperSqlUsesOnlyItsOwnTableFamily() throws Exception {
        for (String mapper : MAPPERS) {
            String admin = resource("mapper/" + mapper);
            String user = resource("mapper/user/User" + mapper);
            assertThat(admin).contains("admin_agent_")
                    .doesNotContain("user_agent_", "admin_admin_agent_");
            assertThat(user).contains("user_agent_").doesNotContain("admin_agent_");
        }
    }

    @Test
    void controllersDependOnTheirOwnDomainService() {
        assertThat(fieldTypes(AgentController.class)).contains(AdminAgentService.class)
                .doesNotContain(UserAgentService.class);
        assertThat(fieldTypes(UserAgentController.class)).contains(UserAgentService.class)
                .doesNotContain(AdminAgentService.class);
    }

    private List<Class<?>> fieldTypes(Class<?> type) {
        return List.of(type.getDeclaredFields()).stream().map(java.lang.reflect.Field::getType).toList();
    }

    private String resource(String path) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
