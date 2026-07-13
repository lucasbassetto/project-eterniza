package com.eterniza.auth.service;

import com.eterniza.auth.domain.Host;
import com.eterniza.auth.dto.*;
import com.eterniza.auth.repository.HostRepository;
import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.exception.UnauthorizedException;
import com.eterniza.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final HostRepository hostRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest req) {
        if (hostRepository.existsByEmail(req.email())) {
            throw new BusinessException("E-mail já cadastrado");
        }
        Host host = hostRepository.save(Host.builder()
                .name(req.name())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .build());

        return new AuthResponse(
                jwtUtil.generateHostToken(host.getId().toString(), host.getEmail()),
                host.getName(),
                host.getEmail()
        );
    }

    public AuthResponse login(LoginRequest req) {
        Host host = hostRepository.findByEmail(req.email())
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (!passwordEncoder.matches(req.password(), host.getPassword())) {
            throw new UnauthorizedException("Credenciais inválidas");
        }
        return new AuthResponse(
                jwtUtil.generateHostToken(host.getId().toString(), host.getEmail()),
                host.getName(),
                host.getEmail()
        );
    }

    public String createGuestSession(GuestSessionRequest req) {
        return jwtUtil.generateGuestToken(req.deviceId(), req.displayName(), req.eventId());
    }
}
