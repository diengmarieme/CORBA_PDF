package com.pdfservice.web.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String role = "USER";   // "USER" ou "ADMIN"
    private String plan = "FREE";   // "FREE", "PRO", "ENTERPRISE"
    private boolean active = true;
    private LocalDateTime createdAt = LocalDateTime.now();
    private long totalOperations = 0;
    private long totalBytesProcessed = 0;

    public long maxFileSizeBytes() {
        if ("PRO".equals(plan))        return 50L * 1024 * 1024;
        if ("ENTERPRISE".equals(plan)) return 200L * 1024 * 1024;
        return 5L * 1024 * 1024; // FREE = 5 Mo
    }

    public User() {}
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }
    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }
    public String getPassword() { return password; }
    public void setPassword(String p) { this.password = p; }
    public String getRole() { return role; }
    public void setRole(String r) { this.role = r; }
    public String getPlan() { return plan; }
    public void setPlan(String p) { this.plan = p; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public long getTotalOperations() { return totalOperations; }
    public void setTotalOperations(long t) { this.totalOperations = t; }
    public long getTotalBytesProcessed() { return totalBytesProcessed; }
    public void setTotalBytesProcessed(long t) { this.totalBytesProcessed = t; }
}
