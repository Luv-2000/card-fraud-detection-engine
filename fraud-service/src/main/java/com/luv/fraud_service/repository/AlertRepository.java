package com.luv.fraud_service.repository;

import com.luv.fraud_service.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    @Query("SELECT a FROM Alert a, Transaction t " +
            "WHERE a.transactionId = t.id AND t.cardId = :cardId " +
            "ORDER BY a.createdAt DESC")
    List<Alert> findAlertsByCardId(@Param("cardId") String cardId);
}