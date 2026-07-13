package com.eterniza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // habilita o job de revelação automática
public class EternizaApplication {
    public static void main(String[] args) {
        SpringApplication.run(EternizaApplication.class, args);
    }
}
