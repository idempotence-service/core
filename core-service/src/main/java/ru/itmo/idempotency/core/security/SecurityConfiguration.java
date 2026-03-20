package ru.itmo.idempotency.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.itmo.idempotency.common.web.ApiResponse;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
                                            ConfiguredTokenAuthenticationFilter configuredTokenAuthenticationFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().hasAnyRole("OPERATOR", "ADMIN")
                )
                .addFilterBefore(configuredTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint((request, response, authException) -> writeNoAccessResponse(response, objectMapper))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeNoAccessResponse(response, objectMapper))
                )
                .logout(AbstractHttpConfigurer::disable)
                .rememberMe(AbstractHttpConfigurer::disable)
                .securityMatcher("/**")
                .with(new org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer<>(), Customizer.withDefaults())
                .build();
    }

    private void writeNoAccessResponse(HttpServletResponse response, ObjectMapper objectMapper) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure("Техническая ошибка", "TECHNICAL_ERROR_01", "Нет доступа"));
    }
}
