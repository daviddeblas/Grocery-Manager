package com.grocerymanager.api.security.config;

import com.grocerymanager.api.security.jwt.JwtAuthentication;
import com.grocerymanager.api.security.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configures Spring Security for the application:
 * - User authentication using `UserDetailsServiceImpl`
 * - JWT-based authentication via `JwtAuthentication`
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables method-level security
public class SecurityConfig {

    /** Custom implementation of UserDetailsService for authentication. */
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    /** JWT authentication filter to validate tokens. */
    @Autowired
    private JwtAuthentication jwtAuthenticationFilter;

    /**
     * - Uses DAO-based authentication
     * - Retrieves user details from UserDetailsServiceImpl
     * - Uses BCrypt hashing for password security
     *
     * @return A configured authentication provider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Provides password encryption using BCrypt (strong hashing algorithm recommended for storing passwords)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures security rules for HTTP requests
     * - Disables CSRF protection (not needed for JWT authentication).
     * - Sets session management to stateless (JWT is stateless).
     * - Defines authorization rules for different endpoints.
     * - Adds JWT authentication filter before the default authentication filter.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // CSRF protection is disabled since we are using JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Enforce stateless session
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Allow public access to authentication endpoints
                        .requestMatchers("/api-docs/**").permitAll() // Allow access to API documentation
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .anyRequest().authenticated() // Require authentication for all other endpoints
                );

        // Use the custom authentication provider
        http.authenticationProvider(authenticationProvider());

        // Add JWT authentication filter before the default Spring Security filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
