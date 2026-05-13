package com.pdfservice.web.config;

import com.pdfservice.web.model.User;
import com.pdfservice.web.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    public CommandLineRunner initData(UserRepository repo, PasswordEncoder enc) {
        return args -> {
            if (!repo.existsByUsername("admin")) {
                User u = new User();
                u.setUsername("admin"); u.setEmail("admin@pdfstudio.com");
                u.setPassword(enc.encode("admin123"));
                u.setRole("ADMIN"); u.setPlan("ENTERPRISE");
                repo.save(u); System.out.println("[Init] admin/admin123 (ADMIN)");
            }
            if (!repo.existsByUsername("demo")) {
                User u = new User();
                u.setUsername("demo"); u.setEmail("demo@pdfstudio.com");
                u.setPassword(enc.encode("demo123"));
                u.setRole("USER"); u.setPlan("FREE");
                repo.save(u); System.out.println("[Init] demo/demo123 (USER/FREE)");
            }
            if (!repo.existsByUsername("pro")) {
                User u = new User();
                u.setUsername("pro"); u.setEmail("pro@pdfstudio.com");
                u.setPassword(enc.encode("pro123"));
                u.setRole("USER"); u.setPlan("PRO");
                repo.save(u); System.out.println("[Init] pro/pro123 (USER/PRO)");
            }
        };
    }
}
