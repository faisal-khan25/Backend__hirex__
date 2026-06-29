package com.hirex.config;

import com.hirex.security.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — UPDATED
 *
 * CHANGES:
 *  NEW: Added /api/live-interview/assignable-applicants/** (MANAGER only)
 *       for the recruiter applicant picker when creating a live session.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Public ────────────────────────────────────────────
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/app/**").permitAll()
                        .requestMatchers("/topic/**").permitAll()
                        .requestMatchers("/api/jobs/browse").permitAll()

                        // ── Admin ─────────────────────────────────────────────
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ── Manager / Recruiter ───────────────────────────────
                        .requestMatchers("/api/manager/**").hasRole("MANAGER")
                        .requestMatchers("/api/ats/**").hasRole("MANAGER")
                        .requestMatchers("/api/jobs/*/ats/**").hasRole("MANAGER")
                        .requestMatchers("/api/jobs/*/shortlisted").hasRole("MANAGER")
                        .requestMatchers("/api/interview/assign-all/**").hasRole("MANAGER")
                        .requestMatchers("/api/interview/assign/**").hasRole("MANAGER")
                        .requestMatchers("/api/chat/manager/**").hasRole("MANAGER")

                        // ── Live Interview ────────────────────────────────────
                        // NOTE: More-specific rules MUST come before generic patterns.
                        .requestMatchers(HttpMethod.POST, "/api/live-interview/create").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET,  "/api/live-interview/join/**").hasAnyRole("MANAGER", "JOBSEEKER")
                        .requestMatchers(HttpMethod.POST, "/api/live-interview/*/end").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET,  "/api/live-interview/\\d+").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET,  "/api/live-interview/candidate/**").hasRole("JOBSEEKER")
                        // NEW: recruiter fetches assignable applicants before creating a session
                        .requestMatchers(HttpMethod.GET,  "/api/live-interview/assignable-applicants/**").hasRole("MANAGER")
                        // ice-servers is authenticated (both roles)
                        .requestMatchers(HttpMethod.GET,  "/api/live-interview/ice-servers").authenticated()
                        // Catch-all for live-interview
                        .requestMatchers("/api/live-interview/**").authenticated()

                        // ── Jobseeker / Candidate ─────────────────────────────
                        .requestMatchers("/api/jobseeker/**").hasRole("JOBSEEKER")
                        .requestMatchers("/api/chat/jobseeker/**").hasRole("JOBSEEKER")

                        // ── Shared authenticated ──────────────────────────────
                        .requestMatchers("/api/chat/files/**").authenticated()
                        .requestMatchers("/api/chat/application/**").authenticated()
                        .requestMatchers("/api/chat/messages/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}