package com.example.platform.config;

import com.example.platform.security.JwtAuthenticationFilter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
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

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins[0]:http://localhost:5173}")
    private String allowedOrigin;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors()
                .and()
                .headers()
                .frameOptions().sameOrigin()
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers(HttpMethod.GET, "/api/notes/share/*").permitAll()
                .antMatchers(HttpMethod.GET, "/api/media-enhancements/capabilities").permitAll()
                .antMatchers(HttpMethod.GET, "/api/resources/**", "/api/categories/**", "/api/comments/**", "/api/search/global", "/api/forum/**", "/api/agent/resources/*/summary", "/api/agent/folders/*/summary").permitAll()
                .antMatchers(HttpMethod.HEAD, "/api/resources/**", "/api/categories/**", "/api/comments/**").permitAll()
                .antMatchers(HttpMethod.POST, "/api/beta/recommendations").permitAll()
                .antMatchers(HttpMethod.POST, "/api/agent/remote/summary").permitAll()
                .antMatchers(HttpMethod.POST, "/api/resources/*/danmakus").permitAll()
                .antMatchers(HttpMethod.GET, "/api/ftp/connections", "/api/ftp/connections/*/list", "/api/ftp/connections/*/search", "/api/ftp/connections/*/preview", "/api/ftp/connections/*/download").permitAll()
                .antMatchers("/uploads/**").permitAll()
                .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) ->
                        writeError(response, HttpStatus.UNAUTHORIZED, 401, "Please login first"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeError(response, HttpStatus.FORBIDDEN, 403, "Access denied"));
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(javax.servlet.http.HttpServletResponse response, HttpStatus status, int code, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                allowedOrigin,
                "http://localhost:3000",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5175",
                "http://127.0.0.1:5175",
                "http://localhost:5188",
                "http://127.0.0.1:5188",
                "http://localhost:5190",
                "http://127.0.0.1:5190"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}


