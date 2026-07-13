package com.eterniza.auth.security;

import com.eterniza.common.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // Rotas públicas — não exigem token
    private static final List<String> PUBLIC = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/guest/session",
            "/api/events/slug/**",        // guest acessa evento pelo slug
            "/api/photos/gallery/**",     // guest visualiza galeria
            "/swagger-ui",
            "/swagger-ui.html",
            "/api-docs"
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Filtro JWT — valida o token em toda requisição autenticada
    @Component
    @RequiredArgsConstructor
    public static class JwtAuthFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;

        @Override
        protected void doFilterInternal(HttpServletRequest req,
                                        HttpServletResponse res,
                                        FilterChain chain) throws ServletException, IOException {
            String header = req.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                String token = header.replace("Bearer ", "");
                if (!jwtUtil.isValid(token)) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                // SPEC_DEVIATION: BACKEND_SPEC.md deixava o SecurityContext vazio
                // ("no futuro"), mas sem isso anyRequest().authenticated() nunca
                // passa para ninguem - nem com token valido - travando toda rota
                // protegida. Populamos o minimo necessario (subject + role) para
                // a autenticacao funcionar; autorizacao granular por role continua
                // fora de escopo.
                String subject = jwtUtil.extractSubject(token);
                String role = jwtUtil.extractRole(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        subject, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (isProtectedRoute(req.getRequestURI())) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            chain.doFilter(req, res);
        }

        private boolean isProtectedRoute(String uri) {
            if (uri == null) return false;
            return !uri.startsWith("/api/auth/") &&
                   !uri.startsWith("/api/events/slug/") &&
                   !uri.startsWith("/api/photos/gallery/") &&
                   !uri.startsWith("/swagger-ui") &&
                   !uri.startsWith("/api-docs");
        }
    }
}
