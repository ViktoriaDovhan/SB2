package com.football.ua.service;

import com.football.ua.model.entity.UserEntity;
import com.football.ua.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🔄 Ініціалізація тестових користувачів...");
        
        try {
            if (userRepository.findByUsername("user").isEmpty()) {
                UserEntity user = new UserEntity();
                user.setUsername("user");
                user.setPassword(passwordEncoder.encode("password"));
                user.setRole(UserEntity.Role.USER);
                user.setEnabled(true);
                userRepository.save(user);
                System.out.println("✅ Створено користувача: user (роль: USER)");
            } else {
                System.out.println("ℹ️ Користувач 'user' вже існує");
            }

            if (userRepository.findByUsername("moderator").isEmpty()) {
                UserEntity moderator = new UserEntity();
                moderator.setUsername("moderator");
                moderator.setPassword(passwordEncoder.encode("password"));
                moderator.setRole(UserEntity.Role.MODERATOR);
                moderator.setEnabled(true);
                userRepository.save(moderator);
                System.out.println("✅ Створено користувача: moderator (роль: MODERATOR)");
            } else {
                System.out.println("ℹ️ Користувач 'moderator' вже існує");
            }

            if (userRepository.findByUsername("editor").isEmpty()) {
                UserEntity editor = new UserEntity();
                editor.setUsername("editor");
                editor.setPassword(passwordEncoder.encode("password"));
                editor.setRole(UserEntity.Role.EDITOR);
                editor.setEnabled(true);
                userRepository.save(editor);
                System.out.println("✅ Створено користувача: editor (роль: EDITOR)");
            } else {
                System.out.println("ℹ️ Користувач 'editor' вже існує");
            }
            
            System.out.println("✅ Ініціалізація користувачів завершена успішно!");
        } catch (Exception e) {
            System.err.println("❌ Помилка при ініціалізації користувачів: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}

