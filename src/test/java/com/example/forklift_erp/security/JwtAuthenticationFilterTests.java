package com.example.forklift_erp.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenForEnabledUserCreatesAuthentication() throws ServletException, IOException {
        JwtAuthenticationFilter filter = filterWithUser("active", true);
        MockHttpServletRequest request = bearerRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("active");
    }

    @Test
    void validTokenForDisabledUserDoesNotAuthenticate() throws ServletException, IOException {
        JwtAuthenticationFilter filter = filterWithUser("disabled", false);
        MockHttpServletRequest request = bearerRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenForDeletedUserDoesNotAuthenticate() throws ServletException, IOException {
        JwtTokenProvider tokenProvider = tokenProviderFor("missing");
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("missing"))
                .thenThrow(new UsernameNotFoundException("missing"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService);
        MockHttpServletRequest request = bearerRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private JwtAuthenticationFilter filterWithUser(String username, boolean enabled) {
        JwtTokenProvider tokenProvider = tokenProviderFor(username);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        var builder = User.withUsername(username).password("password").roles("USER");
        when(userDetailsService.loadUserByUsername(username))
                .thenReturn(enabled ? builder.build() : builder.disabled(true).build());
        return new JwtAuthenticationFilter(tokenProvider, userDetailsService);
    }

    private JwtTokenProvider tokenProviderFor(String username) {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("token")).thenReturn(true);
        when(tokenProvider.getUsername("token")).thenReturn(username);
        return tokenProvider;
    }

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}
