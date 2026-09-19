package com.sky.controller.user;

import com.sky.service.auth.UserAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP security contract for formal authentication endpoints. */
class UserAuthControllerTest {
    @Test
    void smsEndpointNeverReturnsDevelopmentCode() throws Exception {
        UserAuthService service = mock(UserAuthService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserAuthController(service)).build();

        mvc.perform(post("/user/auth/sms/send").contentType("application/json")
                        .content("{\"phone\":\"13800138000\",\"purpose\":\"register\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("246810"))));
        verify(service).sendSms("13800138000", "register");
    }

    @Test
    void logoutClearsSecureRefreshCookie() throws Exception {
        UserAuthService service = mock(UserAuthService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserAuthController(service)).build();

        mvc.perform(post("/user/auth/logout").cookie(new jakarta.servlet.http.Cookie("refresh_token", "raw")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(header -> {
                    String cookie = header.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    org.assertj.core.api.Assertions.assertThat(cookie)
                            .contains("HttpOnly", "Secure", "SameSite=Lax");
                });
        verify(service).logout("raw", false);
    }
}
