package com.football.ua.controller;

import com.football.ua.model.entity.UserEntity;
import com.football.ua.repo.UserRepository;
import com.football.ua.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "🔐 Authentication", description = "API для автентифікації користувачів (PUBLIC)")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public AuthController(UserRepository userRepository, 
                         PasswordEncoder passwordEncoder,
                         JwtUtil jwtUtil,
                         AuthenticationManager authenticationManager,
                         UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Реєстрація користувача", description = "Створює нового користувача з роллю USER")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Користувач з таким іменем вже існує"));
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserEntity.Role.USER);
        user.setEnabled(true);

        userRepository.save(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Користувача успішно зареєстровано");
        response.put("username", user.getUsername());
        response.put("role", user.getRole().name());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Вхід користувача", description = "Автентифікація користувача та отримання JWT токену")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
            String token = jwtUtil.generateToken(userDetails);

            UserEntity user = userRepository.findByUsername(request.username()).orElseThrow();

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("username", user.getUsername());
            response.put("role", user.getRole().name());
            response.put("message", "Успішна автентифікація");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Невірне ім'я користувача або пароль"));
        }
    }

    public record RegisterRequest(
        @NotBlank(message = "Ім'я користувача не може бути порожнім")
        String username,
        
        @NotBlank(message = "Пароль не може бути порожнім")
        String password
    ) {}

    public record LoginRequest(
        @NotBlank(message = "Ім'я користувача не може бути порожнім")
        String username,
        
        @NotBlank(message = "Пароль не може бути порожнім")
        String password
    ) {}
}

