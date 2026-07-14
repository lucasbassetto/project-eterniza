package com.eterniza.photo.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilmFilterServiceTest {

    private final FilmFilterService service = new FilmFilterService();

    @Test
    void buildCommand_vintage_usesVintageToneCurve() {
        List<String> cmd = service.buildCommand("in.jpg", "out.jpg", "VINTAGE");

        assertThat(cmd).containsExactly(
                "convert", "in.jpg",
                "-modulate", "100,80,100",
                "-colorize", "10,5,0",
                "-contrast-stretch", "0.5%",
                "out.jpg");
    }

    @Test
    void buildCommand_blackWhite_convertsToGray() {
        List<String> cmd = service.buildCommand("in.jpg", "out.jpg", "BLACK_WHITE");

        assertThat(cmd).containsExactly(
                "convert", "in.jpg",
                "-colorspace", "Gray",
                "-contrast-stretch", "1%",
                "out.jpg");
    }

    @Test
    void buildCommand_cool_appliesCoolTint() {
        List<String> cmd = service.buildCommand("in.jpg", "out.jpg", "COOL");

        assertThat(cmd).containsExactly(
                "convert", "in.jpg",
                "-modulate", "100,90,105",
                "-colorize", "0,3,12",
                "out.jpg");
    }

    @Test
    void buildCommand_unknownStyle_isPassthroughConvert() {
        List<String> cmd = service.buildCommand("in.jpg", "out.jpg", "ORIGINAL");

        assertThat(cmd).containsExactly("convert", "in.jpg", "out.jpg");
    }

    @Test
    void buildCommand_nullOrUnrecognized_fallsBackToPassthrough() {
        List<String> cmd = service.buildCommand("in.jpg", "out.jpg", "NOPE");

        assertThat(cmd).containsExactly("convert", "in.jpg", "out.jpg");
    }
}
