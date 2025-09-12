package com.ipmonitoring.ipmonitoringapp;

import com.ipmonitoring.ipmonitoringapp.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors();

        // Disable CSRF only for /api/auth/** endpoints
        http.csrf().ignoringRequestMatchers("/api/auth/**");

        http.authorizeHttpRequests(auth -> auth
                // Allow unauthenticated access to /api/auth/** and static resources
                .requestMatchers(
                        "/api/auth/**",
                        "/api/ip/**",
                        "/auth.html",
                        "/clock.html",
                        "/dashboard.html",
                        "/index.html",
                        "/status.html",
                        "/reset-password.html",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/login.css",
                        "/IT-LOGO.png",
                        "/index.css",
                        "/script-index.js",
                        "/script-status.js",
                        "/suprajit_logo_BG.png",
                        "/suprajit_logo.png",
                        "/script.js")
                .permitAll()
                .anyRequest().authenticated());

        http.httpBasic().disable();

        return http.build();
    }
}
