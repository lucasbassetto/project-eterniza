package com.eterniza.auth.security;

import com.eterniza.common.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private static final String SECRET = "eterniza-chave-secreta-de-teste-com-no-minimo-256-bits-1234";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L, 7_200_000L);
    private final SecurityConfig.JwtAuthFilter filter = new SecurityConfig.JwtAuthFilter(jwtUtil);

    @Test
    void doFilter_noAuthorizationHeader_callsChainAndDoesNotSet401() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void doFilter_headerWithoutBearerPrefix_callsChainAndDoesNotSet401() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void doFilter_bearerWithInvalidToken_sets401AndDoesNotCallChain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        String tampered = jwtUtil.generateHostToken("host-123", "lucas@eterniza.com")
                .substring(0, 10) + "tampered-suffix";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + tampered);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_bearerWithValidToken_callsChainAndDoesNotSet401() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        String validToken = jwtUtil.generateHostToken("host-123", "lucas@eterniza.com");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
