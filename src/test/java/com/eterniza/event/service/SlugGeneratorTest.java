package com.eterniza.event.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    @Test
    void slugify_removesAccentsAndSpecialChars() {
        assertThat(SlugGenerator.slugify("Casamento Ana & João"))
                .isEqualTo("casamento-ana-joao");
    }

    @Test
    void slugify_collapsesConsecutiveSeparators() {
        assertThat(SlugGenerator.slugify("Festa   ---  da Empresa!!!"))
                .isEqualTo("festa-da-empresa");
    }

    @Test
    void slugify_trimsHyphensAtEdges() {
        assertThat(SlugGenerator.slugify("  Aniversário da Bia  "))
                .isEqualTo("aniversario-da-bia");
    }

    @Test
    void slugify_truncatesLongNames() {
        String longName = "a".repeat(100);
        assertThat(SlugGenerator.slugify(longName)).hasSize(40);
    }

    @Test
    void slugify_nameWithoutUsableChars_fallsBackToEvento() {
        assertThat(SlugGenerator.slugify("🎉🎊✨")).isEqualTo("evento");
    }

    @Test
    void generate_appendsRandomFourCharSuffix() {
        String slug = SlugGenerator.generate("Casamento Ana & João");

        assertThat(slug).matches("^casamento-ana-joao-[a-z0-9]{4}$");
    }

    @Test
    void generate_producesDifferentSuffixes() {
        // O sufixo aleatório é o que evita colisão entre eventos de mesmo nome
        String slug1 = SlugGenerator.generate("Festa");
        String slug2 = SlugGenerator.generate("Festa");

        assertThat(slug1).isNotEqualTo(slug2);
    }
}
