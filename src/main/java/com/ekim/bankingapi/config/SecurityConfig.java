package com.ekim.bankingapi.config;

import com.ekim.bankingapi.security.JwtAccessDeniedHandler;
import com.ekim.bankingapi.security.JwtAuthEntryPoint;
import com.ekim.bankingapi.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/2fa/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/branches").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*/limits").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customers/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/customer/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}