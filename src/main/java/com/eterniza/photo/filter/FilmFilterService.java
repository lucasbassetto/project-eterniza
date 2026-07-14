package com.eterniza.photo.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class FilmFilterService {

    public byte[] apply(byte[] original, String filmStyle) throws IOException, InterruptedException {
        Path input  = Files.createTempFile("et-in-",  ".jpg");
        Path output = Files.createTempFile("et-out-", ".jpg");
        try {
            Files.write(input, original);
            List<String> cmd = buildCommand(input.toString(), output.toString(), filmStyle);
            int exit = new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();
            if (exit != 0) { log.warn("ImageMagick saiu com código {}", exit); return original; }
            return Files.readAllBytes(output);
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    List<String> buildCommand(String in, String out, String style) {
        return switch (style) {
            case "VINTAGE"     -> List.of("convert", in, "-modulate", "100,80,100", "-colorize", "10,5,0", "-contrast-stretch", "0.5%", out);
            case "BLACK_WHITE" -> List.of("convert", in, "-colorspace", "Gray", "-contrast-stretch", "1%", out);
            case "COOL"        -> List.of("convert", in, "-modulate", "100,90,105", "-colorize", "0,3,12", out);
            default            -> List.of("convert", in, out);
        };
    }
}
