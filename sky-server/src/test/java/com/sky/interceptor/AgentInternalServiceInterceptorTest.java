package com.sky.interceptor;

import com.sky.properties.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentInternalServiceInterceptorTest {

    @Test
    void acceptsMatchingServiceToken() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setInternalServiceToken("a-secure-agent-token-value-1234567890");
        AgentInternalServiceInterceptor interceptor = new AgentInternalServiceInterceptor(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AgentInternalServiceInterceptor.TOKEN_HEADER,
                "a-secure-agent-token-value-1234567890");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void rejectsMissingOrIncorrectServiceToken() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setInternalServiceToken("a-secure-agent-token-value-1234567890");
        AgentInternalServiceInterceptor interceptor = new AgentInternalServiceInterceptor(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void failsClosedWhenServiceTokenIsNotConfigured() throws Exception {
        AgentInternalServiceInterceptor interceptor = new AgentInternalServiceInterceptor(new AgentProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response, new Object()));
        assertEquals(503, response.getStatus());
    }
}
