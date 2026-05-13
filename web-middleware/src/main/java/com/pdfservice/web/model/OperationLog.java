package com.pdfservice.web.model;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class OperationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "username") // C'EST ICI
    private String user; // Stocke le nom de l'utilisateur
    private String operation;
    private boolean success;
    private String errorMessage;
    private long fileSizeBytes;
    private LocalDateTime createdAt = LocalDateTime.now();

    public OperationLog() {}

    public Long getId() { return id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
