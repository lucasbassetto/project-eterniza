package com.eterniza.common.exception;

/** Autenticado, mas sem permissão sobre o recurso (≠ Unauthorized, que é sem identidade). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
