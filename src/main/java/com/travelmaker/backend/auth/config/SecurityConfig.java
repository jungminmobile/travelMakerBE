package com.travelmaker.backend.auth.config;

import com.travelmaker.backend.auth.security.FirebaseAuthenticationFilter;
import com.travelmaker.backend.auth.security.FirebaseTokenVerifier;
import com.travelmaker.backend.auth.security.SecurityErrorWriter;
import jakarta.servlet.DispatcherType;
import com.travelmaker.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   FirebaseTokenVerifier verifier, SecurityErrorWriter errorWriter, UserRepository users,
                                                   @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin)
            throws Exception {
        CorsConfiguration cors = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(config -> config.configurationSource(source))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(response, 401, "UNAUTHENTICATED", "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(response, 403, "FORBIDDEN", "접근 권한이 없습니다.")))
                .addFilterBefore(new FirebaseAuthenticationFilter(verifier, errorWriter, users),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}