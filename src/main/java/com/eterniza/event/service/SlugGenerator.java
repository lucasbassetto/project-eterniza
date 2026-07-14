package com.eterniza.event.service;

import java.security.SecureRandom;
import java.text.Normalizer;

/**
 * Gera slugs legíveis para o link/QR do evento: o nome slugificado + um sufixo
 * aleatório curto ("casamento-ana-joao-x7k2"). O sufixo evita colisão entre
 * eventos de mesmo nome; a unicidade final é garantida pela constraint do banco.
 */
public final class SlugGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 4;
    private static final int MAX_BASE_LENGTH = 40;

    private SlugGenerator() {}

    public static String generate(String name) {
        String base = slugify(name);
        return base + "-" + randomSuffix();
    }

    /** Nome → minúsculas sem acento, palavras separadas por hífen. */
    static String slugify(String name) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")            // remove acentos
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")       // qualquer coisa não alfanumérica vira hífen
                .replaceAll("(^-|-$)", "");          // sem hífen nas pontas
        if (slug.length() > MAX_BASE_LENGTH) {
            slug = slug.substring(0, MAX_BASE_LENGTH).replaceAll("-$", "");
        }
        // Nome sem nenhum caractere aproveitável (ex.: só emojis)
        return slug.isEmpty() ? "evento" : slug;
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return sb.toString();
    }
}
