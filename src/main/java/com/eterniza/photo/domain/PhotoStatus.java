package com.eterniza.photo.domain;

public enum PhotoStatus {
    // A foto é sempre armazenada já finalizada (filtro aplicado no app);
    // não há processamento no servidor, então READY é o único estado.
    READY
}
