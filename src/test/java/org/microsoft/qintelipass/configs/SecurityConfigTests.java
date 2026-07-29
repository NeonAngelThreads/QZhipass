package org.microsoft.qintelipass.configs;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTests {
    @Test
    void allowsConfiguredFrontendOriginWithCredentialsAndAuthorizationHeader() throws Exception {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource("http://localhost:5173");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS",
                "/api/v1/account/email-binding"
        );
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        boolean accepted = new DefaultCorsProcessor().processRequest(
                configuration,
                request,
                response
        );

        assertThat(accepted).isTrue();
        assertThat(response.getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:5173");
        assertThat(response.getHeader("Access-Control-Allow-Credentials"))
                .isEqualTo("true");
        assertThat(response.getHeader("Access-Control-Allow-Headers"))
                .containsIgnoringCase("authorization")
                .containsIgnoringCase("content-type");
    }
}
