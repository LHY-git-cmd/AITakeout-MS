package com.sky.controller.admin;

import com.sky.context.BaseContext;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.interceptor.AdminPermissionInterceptor;
import com.sky.mapper.EmployeeMapper;
import com.sky.service.account.AccountService;
import com.sky.service.security.AdminAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminMockAccountAuthorizationHttpTest {
    @Mock private AccountService accountService;
    @Mock private EmployeeMapper employeeMapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        BaseContext.setCurrentRole("ADMIN");
        mvc = MockMvcBuilders.standaloneSetup(new AdminMockAccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AdminPermissionInterceptor(new AdminAuthorizationService(employeeMapper)))
                .build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void ordinaryAdminCanReadAccountsButCannotAdjustBalance() throws Exception {
        mvc.perform(get("/admin/mock-account")).andExpect(status().isOk());
        mvc.perform(post("/admin/mock-account/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":7,"deltaCent":100,"reason":"test","idempotencyKey":"key-1"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("当前管理员无权执行该操作"));
    }
}
