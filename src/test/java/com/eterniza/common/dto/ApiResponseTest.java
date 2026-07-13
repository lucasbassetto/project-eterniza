package com.eterniza.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void okWithDataOnly_setsSuccessTrueAndNullMessage() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getMessage()).isNull();
    }

    @Test
    void okWithMessageAndData_setsAllFields() {
        ApiResponse<String> response = ApiResponse.ok("Criado com sucesso", "payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Criado com sucesso");
        assertThat(response.getData()).isEqualTo("payload");
    }

    @Test
    void error_setsSuccessFalseAndNullData() {
        ApiResponse<Void> response = ApiResponse.error("Algo deu errado");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Algo deu errado");
        assertThat(response.getData()).isNull();
    }

    @Test
    void jsonSerialization_omitsNullFields() throws Exception {
        ApiResponse<String> response = ApiResponse.ok("payload");
        String json = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(response);

        assertThat(json).doesNotContain("\"message\"");
        assertThat(json).contains("\"data\":\"payload\"");
    }
}
