package com.eterniza.auth.controller;

import com.eterniza.auth.dto.RegisterRequest;
import com.eterniza.auth.repository.HostRepository;
import com.eterniza.common.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eterniza")
            .withUsername("eterniza")
            .withPassword("eterniza123");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HostRepository hostRepository;
    @Autowired private JwtUtil jwtUtil;

    @BeforeEach
    void cleanUp() {
        hostRepository.deleteAll();
    }

    @Test
    void register_validPayload_returns201WithToken() throws Exception {
        RegisterRequest req = new RegisterRequest("Lucas", "lucas@eterniza.com", "senha1234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.name").value("Lucas"))
                .andExpect(jsonPath("$.data.email").value("lucas@eterniza.com"));
    }

    @Test
    void register_duplicateEmail_returns400WithMessage() throws Exception {
        RegisterRequest req = new RegisterRequest("Lucas", "lucas@eterniza.com", "senha1234");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado"));
    }

    @Test
    void register_invalidPayload_returns400WithJoinedValidationMessages() throws Exception {
        String invalidJson = """
                {"name": "", "email": "nao-e-email", "password": "123"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Nome é obrigatório")))
                .andExpect(jsonPath("$.message").value(containsString("Senha deve ter no mínimo 8 caracteres")));
    }

    @Test
    void login_correctCredentials_returns200WithToken() throws Exception {
        registerHost("Lucas", "lucas@eterniza.com", "senha1234");

        String loginJson = """
                {"email": "lucas@eterniza.com", "password": "senha1234"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    void login_emailNotFound_returns401WithMessage() throws Exception {
        String loginJson = """
                {"email": "desconhecido@eterniza.com", "password": "senha1234"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    void login_wrongPassword_returns401WithSameMessage() throws Exception {
        registerHost("Lucas", "lucas@eterniza.com", "senha1234");

        String loginJson = """
                {"email": "lucas@eterniza.com", "password": "senha-errada"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    void guestSession_validPayload_returns200WithToken() throws Exception {
        String json = """
                {"displayName": "Ana", "eventId": "event-999", "deviceId": "device-abc"}
                """;

        mockMvc.perform(post("/api/auth/guest/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void guestSession_invalidPayload_returns400WithValidationMessage() throws Exception {
        String json = """
                {"displayName": "", "eventId": "event-999", "deviceId": "device-abc"}
                """;

        mockMvc.perform(post("/api/auth/guest/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Nome de exibição é obrigatório")));
    }

    @Test
    void publicRoute_withoutAuthorizationHeader_isNotBlockedBySecurityChain() throws Exception {
        String loginJson = """
                {"email": "desconhecido@eterniza.com", "password": "senha1234"}
                """;

        // 401 vem da regra de negocio (AuthService), nao da cadeia de seguranca - prova que a rota publica nao foi bloqueada
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    void protectedRoute_withoutAuthorizationHeader_isBlockedBySecurityChain() throws Exception {
        // Spring Security 6 sem AuthenticationEntryPoint customizado: usuario anonimo
        // falha anyRequest().authenticated() e recebe 403 (nao 401) do AccessDeniedHandler padrao.
        MvcResult result = mockMvc.perform(get("/api/protected-route-not-mapped-yet"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void protectedRoute_withValidBearerToken_passesThroughSecurityChain() throws Exception {
        String token = jwtUtil.generateHostToken("host-123", "lucas@eterniza.com");

        MvcResult result = mockMvc.perform(get("/api/protected-route-not-mapped-yet")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        // Nao ha controller real mapeado para este path sintetico (Fase 4 ainda nao tem
        // rotas de negocio protegidas) - o que importa aqui e que a cadeia de seguranca
        // NAO bloqueou com 401/403; o status downstream exato (404/500) e incidental ao
        // roteamento, fora do escopo desta AC.
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void response_neverSetsJsessionIdCookie_confirmingStatelessPolicy() throws Exception {
        String loginJson = """
                {"email": "desconhecido@eterniza.com", "password": "senha1234"}
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNull();
    }

    private void registerHost(String name, String email, String password) throws Exception {
        RegisterRequest req = new RegisterRequest(name, email, password);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }
}
