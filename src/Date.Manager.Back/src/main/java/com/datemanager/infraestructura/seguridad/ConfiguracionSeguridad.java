package com.datemanager.infraestructura.seguridad;

import java.util.Arrays;
import java.util.List;

import com.datemanager.api.RespuestasSeguridad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API sin estado con token Bearer (JWT). Sin token: login, {@code /api/publico/**} y Swagger (solo si está
 * activo, es decir, en {@code dev}); todo lo demás exige token. Los permisos se comprueban por operación con
 * {@code @PreAuthorize("@permisos.tiene(...)")}.
 * <p>
 * En el login y en la zona del cliente ni siquiera se lee la cabecera {@code Authorization}: un token vencido del
 * personal que quedó en el mismo navegador no debe romper el login ni la zona del cliente con un 401.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, RespuestasSeguridad respuestas,
            ConvertidorToken convertidor, @Value("${springdoc.swagger-ui.enabled:false}") boolean swagger)
            throws Exception {
        PathPatternRequestMatcher.Builder patron = PathPatternRequestMatcher.withDefaults();
        RequestMatcher sinLeerToken = new OrRequestMatcher(
                patron.matcher(HttpMethod.POST, "/api/auth/login"),
                patron.matcher("/api/publico/**"));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> {
                    rutas.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
                    rutas.requestMatchers("/api/publico/**", "/error").permitAll();
                    if (swagger) {
                        rutas.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    rutas.anyRequest().authenticated();
                })
                .oauth2ResourceServer(recurso -> recurso
                        .bearerTokenResolver(resolutorToken(sinLeerToken))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas));
        return http.build();
    }

    /** Como el de Spring, pero sin token en las rutas de {@code sinLeerToken}. */
    static BearerTokenResolver resolutorToken(RequestMatcher sinLeerToken) {
        DefaultBearerTokenResolver normal = new DefaultBearerTokenResolver();
        return peticion -> sinLeerToken.matches(peticion) ? null : normal.resolve(peticion);
    }

    @Bean
    JwtDecoder jwtDecoder(ServicioJwt servicioJwt) {
        return servicioJwt.decodificador();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Orígenes separados por comas en {@code DATEMANAGER_CORS_ORIGENES} (por defecto el Angular de desarrollo). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${datemanager.cors-origenes}") String origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origenes.split(","))
                .map(String::strip)
                .filter(origen -> !origen.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}
