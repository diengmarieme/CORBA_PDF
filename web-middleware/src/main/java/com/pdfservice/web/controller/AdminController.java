package com.pdfservice.web.controller;

import com.pdfservice.web.model.OperationLog;
import com.pdfservice.web.model.User;
import com.pdfservice.web.repository.OperationLogRepository;
import com.pdfservice.web.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepo;
    private final OperationLogRepository logRepo;

    public AdminController(UserRepository userRepo, OperationLogRepository logRepo) {
        this.userRepo = userRepo; this.logRepo = logRepo;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers",      userRepo.count());
        stats.put("totalAdmins",     userRepo.countByRole("ADMIN"));
        stats.put("totalClients",    userRepo.countByRole("USER"));
        stats.put("freeUsers",       userRepo.countByPlan("FREE"));
        stats.put("proUsers",        userRepo.countByPlan("PRO"));
        stats.put("enterpriseUsers", userRepo.countByPlan("ENTERPRISE"));
        stats.put("totalOperations", logRepo.count());
        stats.put("successOps",      logRepo.countBySuccess(true));
        stats.put("failedOps",       logRepo.countBySuccess(false));
        List<Object[]> opStats = logRepo.countByOperation();
        Map<String, Long> opMap = new LinkedHashMap<>();
        for (Object[] row : opStats) opMap.put((String)row[0], (Long)row[1]);
        stats.put("operationStats", opMap);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        return ResponseEntity.ok(userRepo.findAll().stream().map(this::toMap).collect(Collectors.toList()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return userRepo.findById(id).map(u -> ResponseEntity.ok(toMap(u))).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/toggle")
    public ResponseEntity<?> toggleUser(@PathVariable Long id) {
        return userRepo.findById(id).map(user -> {
            user.setActive(!user.isActive());
            userRepo.save(user);
            return ResponseEntity.ok(Map.of("id", user.getId(), "active", user.isActive(),
                "message", user.isActive() ? "Compte activé" : "Compte désactivé"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/plan")
    public ResponseEntity<?> changePlan(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String plan = body.get("plan");
        if (!List.of("FREE","PRO","ENTERPRISE").contains(plan))
            return ResponseEntity.badRequest().body(Map.of("error", "Plan invalide"));
        return userRepo.findById(id).map(user -> {
            user.setPlan(plan);
            userRepo.save(user);
            return ResponseEntity.ok(Map.of("plan", user.getPlan(), "maxFileSize", user.maxFileSizeBytes()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepo.existsById(id)) return ResponseEntity.notFound().build();
        userRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé"));
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs() {
        return ResponseEntity.ok(logRepo.findTop50ByOrderByCreatedAtDesc()
            .stream().map(l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", l.getId());
                m.put("username", l.getUser() != null ? l.getUser() : "anonymous");
                m.put("operation", l.getOperation());
                m.put("success", l.isSuccess());
                m.put("fileSizeBytes", l.getFileSizeBytes());
                m.put("createdAt", l.getCreatedAt().toString());
                return m;
            }).collect(Collectors.toList()));
    }

    private Map<String, Object> toMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId()); m.put("username", u.getUsername());
        m.put("email", u.getEmail()); m.put("role", u.getRole());
        m.put("plan", u.getPlan()); m.put("active", u.isActive());
        m.put("createdAt", u.getCreatedAt().toString());
        m.put("totalOperations", u.getTotalOperations());
        m.put("totalBytesProcessed", u.getTotalBytesProcessed());
        m.put("maxFileSize", u.maxFileSizeBytes());
        return m;
    }
}
