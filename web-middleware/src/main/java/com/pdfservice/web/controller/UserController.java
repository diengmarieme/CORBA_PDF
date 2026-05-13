package com.pdfservice.web.controller;

import com.pdfservice.web.model.User;
import com.pdfservice.web.repository.UserRepository;
import com.pdfservice.web.repository.OperationLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepo;
    private final OperationLogRepository logRepo;

    public UserController(UserRepository userRepo, OperationLogRepository logRepo) {
        this.userRepo = userRepo; this.logRepo = logRepo;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication auth) {
        return userRepo.findByUsername(auth.getName()).map(user -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",                  user.getId());
            m.put("username",            user.getUsername());
            m.put("email",               user.getEmail());
            m.put("role",                user.getRole());
            m.put("plan",                user.getPlan());
            m.put("maxFileSize",         user.maxFileSizeBytes());
            m.put("totalOperations",     user.getTotalOperations());
            m.put("totalBytesProcessed", user.getTotalBytesProcessed());
            m.put("createdAt",           user.getCreatedAt().toString());
            return ResponseEntity.ok(m);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getMyLogs(Authentication auth) {
        List<Map<String, Object>> logs = logRepo
            .findByUserOrderByCreatedAtDesc(auth.getName())
            .stream().limit(20).map(l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("operation",     l.getOperation());
                m.put("success",       l.isSuccess());
                m.put("fileSizeBytes", l.getFileSizeBytes());
                m.put("createdAt",     l.getCreatedAt().toString());
                return m;
            }).collect(Collectors.toList());
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/upgrade")
    public ResponseEntity<?> upgradePlan(@RequestBody Map<String, String> body, Authentication auth) {
        String plan = body.get("plan");
        if (!List.of("FREE","PRO","ENTERPRISE").contains(plan))
            return ResponseEntity.badRequest().body(Map.of("error", "Plan invalide"));
        return userRepo.findByUsername(auth.getName()).map(user -> {
            user.setPlan(plan);
            userRepo.save(user);
            return ResponseEntity.ok(Map.of(
                "plan", user.getPlan(),
                "maxFileSize", user.maxFileSizeBytes(),
                "message", "Plan mis à jour vers " + plan));
        }).orElse(ResponseEntity.notFound().build());
    }
}
