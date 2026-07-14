package com.eterniza.event.controller;

import com.eterniza.event.dto.CreateEventRequest;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.repository.PhotoRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EventControllerTest {

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
    @Autowired private EventRepository eventRepository;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private JwtUtil jwtUtil;

    private UUID hostId;
    private String hostToken;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        eventRepository.deleteAll();
        hostId = UUID.randomUUID();
        hostToken = jwtUtil.generateHostToken(hostId.toString(), "host@eterniza.com");
    }

    // ─── photoCount reflete as fotos reais da tabela (não um contador denormalizado) ───
    @Test
    void findBySlug_eventWithPhotos_photoCountReflectsRealPhotos() throws Exception {
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest("Evento com fotos", futureTime, null);

        MvcResult createResult = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hostToken)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.photoCount").value(0))
                .andReturn();

        var body = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        UUID eventId = UUID.fromString(body.get("id").asText());
        String slug = body.get("slug").asText();

        photoRepository.save(photo(eventId, "k1"));
        photoRepository.save(photo(eventId, "k2"));

        mockMvc.perform(get("/api/events/slug/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoCount").value(2));
    }

    private Photo photo(UUID eventId, String key) {
        return Photo.builder()
                .eventId(eventId)
                .guestDeviceId("device-1")
                .guestName("Ana")
                .originalKey(key)
                .status(PhotoStatus.READY)
                .build();
    }

    // ─── EVENT-01: Create event with valid payload ───
    @Test
    void create_validPayload_returns201WithEventResponse() throws Exception {
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest("Meu Evento", futureTime, 10);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hostToken)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("Meu Evento"))
                .andExpect(jsonPath("$.data.slug").exists())
                .andExpect(jsonPath("$.data.qrCodeUrl").exists())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.guestLimit").value(10))
                .andExpect(jsonPath("$.data.guestCount").value(0))
                .andExpect(jsonPath("$.data.photoCount").value(0));
    }

    // ─── EVENT-02: Create with invalid payload ───
    @Test
    void create_invalidPayload_returns400WithValidationMessages() throws Exception {
        String invalidJson = """
                {"name": "", "revealAt": "2020-01-01T00:00:00Z"}
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hostToken)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("obrigatório")));
    }

    // ─── EVENT-03: Create without authentication ───
    @Test
    void create_withoutAuthorizationHeader_returns401() throws Exception {
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest("Event", futureTime, null);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ─── EVENT-05: Find by slug - success ───
    @Test
    void findBySlug_existingSlug_returns200WithEventResponse() throws Exception {
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest("Event Pub", futureTime, null);

        MvcResult createResult = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hostToken)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String slug = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("slug").asText();

        mockMvc.perform(get("/api/events/slug/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Event Pub"))
                .andExpect(jsonPath("$.data.slug").value(slug));
    }

    // ─── EVENT-06: Find by slug - not found ───
    @Test
    void findBySlug_nonExistentSlug_returns404() throws Exception {
        mockMvc.perform(get("/api/events/slug/nonexistent-slug"))
                .andExpect(status().isNotFound());
    }

    // ─── EVENT-12: Find by slug public route ───
    @Test
    void findBySlug_isPublicRoute_noAuthRequired() throws Exception {
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest("Public Event", futureTime, null);

        MvcResult createResult = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hostToken)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String slug = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("slug").asText();

        // Access public route without token
        mockMvc.perform(get("/api/events/slug/{slug}", slug)
                        .header("Authorization", ""))
                .andExpect(status().isOk());
    }

    // ─── EVENT-07: Find by host - success ───
    @Test
    void myEvents_validToken_returns200WithEventList() throws Exception {
        Instant futureTime = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateEventRequest req1 = new CreateEventRequest("Event 1", futureTime, null);
        CreateEventRequest req2 = new CreateEventRequest("Event 2", futureTime.plus(1, ChronoUnit.DAYS), null);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hostToken)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hostToken)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/events/my")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ─── EVENT-07: Find by host - empty list ───
    @Test
    void myEvents_noEvents_returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/events/my")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ─── EVENT-08: Find by host - requires authentication ───
    @Test
    void myEvents_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/events/my"))
                .andExpect(status().isUnauthorized());
    }
}
