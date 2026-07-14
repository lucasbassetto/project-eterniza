package com.eterniza.photo.domain;

public enum PhotoStatus {
    // A foto é sempre armazenada já finalizada (filtro aplicado no app);
    // não há processamento no servidor.
    READY,
    // Soft delete (moderação do host): some da galeria, mas a linha permanece —
    // a "pose" do convidado continua gasta (o limite conta todos os status).
    DELETED
}
