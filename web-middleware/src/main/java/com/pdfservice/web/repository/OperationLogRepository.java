package com.pdfservice.web.repository;

import com.pdfservice.web.model.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    List<OperationLog> findByUserOrderByCreatedAtDesc(String user);
    List<OperationLog> findTop50ByOrderByCreatedAtDesc();
    long countBySuccess(boolean success);
    @Query("SELECT o.operation, COUNT(o) FROM OperationLog o GROUP BY o.operation")
    List<Object[]> countByOperation();
}
