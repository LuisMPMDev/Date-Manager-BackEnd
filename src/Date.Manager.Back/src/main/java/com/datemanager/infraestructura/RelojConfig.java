package com.datemanager.infraestructura;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Único origen de "ahora": un reloj en la zona del negocio (no la de la JVM). */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(@Value("${datemanager.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
