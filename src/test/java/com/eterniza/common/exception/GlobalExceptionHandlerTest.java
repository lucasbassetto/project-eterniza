package com.eterniza.common.exception;

import com.eterniza.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusiness_returns400WithMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException("E-mail já cadastrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("E-mail já cadastrado");
    }

    @Test
    void handleNotFound_returns404WithFormattedMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new NotFoundException("Evento", "abc-123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Evento não encontrado: abc-123");
    }

    @Test
    void handleUnauthorized_returns401WithMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnauthorized(new UnauthorizedException("Credenciais inválidas"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void handleValidation_returns400WithJoinedFieldErrorMessages() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "E-mail é obrigatório"));
        bindingResult.addError(new FieldError("request", "password", "Senha deve ter no mínimo 8 caracteres"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("E-mail é obrigatório, Senha deve ter no mínimo 8 caracteres");
        // errors traz campo → mensagem para o app destacar o campo no formulário
        assertThat(response.getBody().getErrors()).containsExactly(
                entry("email", "E-mail é obrigatório"),
                entry("password", "Senha deve ter no mínimo 8 caracteres"));
    }

    @Test
    void handleValidation_multipleViolationsOnSameField_keepsFirstInErrorsMap() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "E-mail é obrigatório"));
        bindingResult.addError(new FieldError("request", "email", "E-mail inválido"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getBody().getErrors())
                .containsExactly(entry("email", "E-mail é obrigatório"));
        // O message continua listando todas
        assertThat(response.getBody().getMessage())
                .isEqualTo("E-mail é obrigatório, E-mail inválido");
    }

    @Test
    void handleBusiness_hasNoErrorsField() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException("Arquivo vazio"));

        // errors só existe em erro de validação de payload
        assertThat(response.getBody().getErrors()).isNull();
    }

    @Test
    void handleGeneric_returns500WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneric(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Erro interno. Tente novamente.");
    }
}
