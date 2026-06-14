package com.example.forklift_erp;

import com.example.forklift_erp.security.JwtAuthenticationFilter;
import com.example.forklift_erp.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledUserTokenIsRejectedBeforeSecurityContextIsAuthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        AuthenticationEntryPoint authenticationEntryPoint = mock(AuthenticationEntryPoint.class);
        ReflectionTestUtils.setField(filter, "jwtTokenProvider", tokenProvider);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        ReflectionTestUtils.setField(filter, "authenticationEntryPoint", authenticationEntryPoint);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer issued-before-disable");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain filterChain = (servletRequest, servletResponse) -> chainCalled.set(true);

        when(tokenProvider.validateToken("issued-before-disable")).thenReturn(true);
        when(tokenProvider.getUsername("issued-before-disable")).thenReturn("disabled-user");
        when(userDetailsService.loadUserByUsername("disabled-user")).thenReturn(User.withUsername("disabled-user")
                .password("encoded-password")
                .disabled(true)
                .authorities("ROLE_USER")
                .build());

        filter.doFilter(request, response, filterChain);

        assertThat(chainCalled).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authenticationEntryPoint).commence(eq(request), eq(response), any(DisabledException.class));
    }
}
