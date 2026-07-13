package com.eterniza.common.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String resource, Object id) {
        super("%s não encontrado: %s".formatted(resource, id));
    }
}
