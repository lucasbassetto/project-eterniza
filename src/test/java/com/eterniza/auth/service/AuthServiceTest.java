package com.eterniza.auth.service;

import com.eterniza.auth.domain.Host;
import com.eterniza.auth.dto.*;
import com.eterniza.auth.repository.HostRepository;
import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.exception.UnauthorizedException;
import com.eterniza.common.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private static final String SECRET = "eterniza-chave-secreta-de-teste-com-no-minimo-256-bits-1234";

    private HostRepository hostRepository;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        hostRepository = mock(HostRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = new JwtUtil(SECRET, 3_600_000L, 7_200_000L);
        authService = new AuthService(hostRepository, passwordEncoder, jwtUtil);
    }

    @Test
    void register_newEmail_savesHostWithEncodedPasswordAndReturnsHostToken() {
        UUID hostId = UUID.randomUUID();
        RegisterRequest req = new RegisterRequest("Lucas", "lucas@eterniza.com", "senha1234");

        when(hostRepository.existsByEmail("lucas@eterniza.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("hashed-senha1234");
        when(hostRepository.save(any(Host.class))).thenAnswer(invocation -> {
            Host h = invocation.getArgument(0);
            h.setId(hostId);
            return h;
        });

        AuthResponse response = authService.register(req);

        ArgumentCaptor<Host> captor = ArgumentCaptor.forClass(Host.class);
        verify(hostRepository).save(captor.capture());
        Host saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Lucas");
        assertThat(saved.getEmail()).isEqualTo("lucas@eterniza.com");
        assertThat(saved.getPassword()).isEqualTo("hashed-senha1234");

        assertThat(response.name()).isEqualTo("Lucas");
        assertThat(response.email()).isEqualTo("lucas@eterniza.com");

        Claims claims = jwtUtil.extractClaims(response.token());
        assertThat(claims.getSubject()).isEqualTo(hostId.toString());
        assertThat(claims.get("role")).isEqualTo("HOST");
        assertThat(claims.get("email")).isEqualTo("lucas@eterniza.com");
    }

    @Test
    void register_duplicateEmail_throwsBusinessExceptionAndDoesNotSave() {
        RegisterRequest req = new RegisterRequest("Lucas", "lucas@eterniza.com", "senha1234");
        when(hostRepository.existsByEmail("lucas@eterniza.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));

        assertThat(ex.getMessage()).isEqualTo("E-mail já cadastrado");
        verify(hostRepository, never()).save(any());
    }

    @Test
    void login_correctCredentials_returnsNewHostToken() {
        UUID hostId = UUID.randomUUID();
        Host host = Host.builder()
                .id(hostId)
                .name("Lucas")
                .email("lucas@eterniza.com")
                .password("hashed-senha1234")
                .build();

        LoginRequest req = new LoginRequest("lucas@eterniza.com", "senha1234");
        when(hostRepository.findByEmail("lucas@eterniza.com")).thenReturn(Optional.of(host));
        when(passwordEncoder.matches("senha1234", "hashed-senha1234")).thenReturn(true);

        AuthResponse response = authService.login(req);

        assertThat(response.name()).isEqualTo("Lucas");
        assertThat(response.email()).isEqualTo("lucas@eterniza.com");

        Claims claims = jwtUtil.extractClaims(response.token());
        assertThat(claims.getSubject()).isEqualTo(hostId.toString());
        assertThat(claims.get("role")).isEqualTo("HOST");
    }

    @Test
    void login_emailNotFound_throwsUnauthorizedException() {
        LoginRequest req = new LoginRequest("desconhecido@eterniza.com", "senha1234");
        when(hostRepository.findByEmail("desconhecido@eterniza.com")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req));

        assertThat(ex.getMessage()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedExceptionSameMessage() {
        Host host = Host.builder()
                .id(UUID.randomUUID())
                .name("Lucas")
                .email("lucas@eterniza.com")
                .password("hashed-senha1234")
                .build();

        LoginRequest req = new LoginRequest("lucas@eterniza.com", "senha-errada");
        when(hostRepository.findByEmail("lucas@eterniza.com")).thenReturn(Optional.of(host));
        when(passwordEncoder.matches("senha-errada", "hashed-senha1234")).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req));

        assertThat(ex.getMessage()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void createGuestSession_returnsTokenWithDeviceIdRoleAndClaims() {
        GuestSessionRequest req = new GuestSessionRequest("Ana", "event-999", "device-abc");

        String token = authService.createGuestSession(req);
        Claims claims = jwtUtil.extractClaims(token);

        assertThat(claims.getSubject()).isEqualTo("device-abc");
        assertThat(claims.get("role")).isEqualTo("GUEST");
        assertThat(claims.get("displayName")).isEqualTo("Ana");
        assertThat(claims.get("eventId")).isEqualTo("event-999");
    }
}
