package com.curtinhonestly.backend.security;

import com.curtinhonestly.backend.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        log.debug("SecurityConfig loaded");

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Allow CORS preflight requests
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()

                        // Public read endpoints for the student app
                        .requestMatchers(HttpMethod.GET, "/units", "/units/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        .requestMatchers("/campaigns/validate").permitAll()
                        .requestMatchers("/auth/me", "/auth/verify-student").authenticated()
                        .requestMatchers("/error").permitAll()

                        // Admin namespace and sensitive listings
                        .requestMatchers("/admin/**").hasAuthority(UserRole.ROLE_ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/reviews/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/reviews", "/reviews/**").hasAuthority(UserRole.ROLE_ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/users", "/users/**").hasAuthority(UserRole.ROLE_ADMIN.name())

                        // Admin only endpoints - Use name() to get "ROLE_ADMIN"
                        .requestMatchers(HttpMethod.DELETE, "/units/**").hasAuthority(UserRole.ROLE_ADMIN.name())
                        .requestMatchers(HttpMethod.POST, "/units", "/units/**").hasAuthority(UserRole.ROLE_ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/users/**").hasAuthority(UserRole.ROLE_ADMIN.name())

                        // User endpoints - Use name() to get "ROLE_USER"
                        .requestMatchers(HttpMethod.POST, "/reviews", "/reviews/**").hasAnyAuthority(UserRole.ROLE_USER.name(), UserRole.ROLE_ADMIN.name())
                        // Unlike is DELETE /reviews/{id}/likes (owner of the like). Review delete itself
                        // is further gated by @PreAuthorize(IS_ADMIN_OR_OWNER) on the resource method.
                        .requestMatchers(HttpMethod.DELETE, "/reviews/*/likes").hasAnyAuthority(UserRole.ROLE_USER.name(), UserRole.ROLE_ADMIN.name())

                        // Default
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Exact origin allowlist, driven by app.cors.allowed-origins (env: CORS_ALLOWED_ORIGINS).
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

