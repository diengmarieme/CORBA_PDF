package com.pdfservice.web.controller;

import com.pdfservice.web.model.User;
import com.pdfservice.web.repository.UserRepository;
import com.pdfservice.web.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepo, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepo = userRepo; this.encoder = encoder; this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        Optional<User> opt = userRepo.findByUsername(body.get("username"));
        if (opt.isEmpty() || !encoder.matches(body.get("password"), opt.get().getPassword()))
            return ResponseEntity.status(401).body(Map.of("error", "Identifiants incorrects"));
        User user = opt.get();
        if (!user.isActive())
            return ResponseEntity.status(403).body(Map.of("error", "Compte désactivé"));
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.ok(buildResponse(user, token));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");
        if (username == null || username.length() < 3)
            return ResponseEntity.badRequest().body(Map.of("error", "Nom d'utilisateur trop court"));
        if (email == null || !email.contains("@"))
            return ResponseEntity.badRequest().body(Map.of("error", "Email invalide"));
        if (password == null || password.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("error", "Mot de passe trop court (min 6)"));
        if (userRepo.existsByUsername(username))
            return ResponseEntity.badRequest().body(Map.of("error", "Nom d'utilisateur déjà pris"));
        if (userRepo.existsByEmail(email))
            return ResponseEntity.badRequest().body(Map.of("error", "Email déjà utilisé"));
        User user = new User();
        user.setUsername(username); user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setRole("USER"); user.setPlan("FREE");
        userRepo.save(user);
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.ok(buildResponse(user, token));
    }

    private Map<String, Object> buildResponse(User user, String token) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token", token); r.put("username", user.getUsername());
        r.put("email", user.getEmail()); r.put("role", user.getRole());
        r.put("plan", user.getPlan()); r.put("maxFileSize", user.maxFileSizeBytes());
        return r;
    }
}
