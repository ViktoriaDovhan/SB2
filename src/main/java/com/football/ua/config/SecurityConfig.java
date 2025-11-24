package com.football.ua.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/", "/*.html").permitAll()
                .requestMatchers("/css/**", "/js/**", "/openapi.json").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/api/access/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/news", "/api/news/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/matches", "/api/matches/*", "/api/matches/teams/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/teams", "/api/teams/*", "/api/teams/actual", "/api/teams/standings/*", "/api/teams/scorers/**", "/api/teams/leagues", "/api/teams/matches/**", "/api/teams/cache/info").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/teams/cache/**").authenticated()
                .requestMatchers("/api/cache/**").hasRole("MODERATOR")
                .requestMatchers(HttpMethod.GET, "/api/forum/topics", "/api/forum/topics/*/posts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/upcoming-matches/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/player-of-the-week").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/activity").hasRole("MODERATOR")
                .requestMatchers("/activity.html", "/activity").hasRole("MODERATOR")
                .requestMatchers(HttpMethod.POST, "/api/forum/topics").hasAnyRole("USER", "MODERATOR", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/forum/topics/*/posts").hasAnyRole("USER", "MODERATOR", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/news/*/like").hasAnyRole("USER", "MODERATOR", "EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/matches/*/subscribe").hasAnyRole("USER", "MODERATOR", "EDITOR")
                .requestMatchers(HttpMethod.DELETE, "/api/forum/topics/*").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/forum/topics/*/posts/*").authenticated()
                .requestMatchers("/api/moderator/**").hasRole("MODERATOR")
                .requestMatchers(HttpMethod.POST, "/api/news").hasRole("EDITOR")
                .requestMatchers(HttpMethod.PUT, "/api/news/*").hasRole("EDITOR")
                .requestMatchers(HttpMethod.DELETE, "/api/news/*").hasRole("EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/matches").hasRole("EDITOR")
                .requestMatchers(HttpMethod.PATCH, "/api/matches/*/score").hasRole("EDITOR")
                .requestMatchers(HttpMethod.DELETE, "/api/matches/*").hasRole("EDITOR")
                .requestMatchers(HttpMethod.POST, "/api/teams").hasRole("EDITOR")
                .requestMatchers(HttpMethod.PUT, "/api/teams/*").hasRole("EDITOR")
                .requestMatchers(HttpMethod.DELETE, "/api/teams/*").hasRole("EDITOR")
                .requestMatchers(HttpMethod.POST, "/ui/feedback/delete/*").hasAnyRole("EDITOR", "MODERATOR")
                .requestMatchers("/ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}

