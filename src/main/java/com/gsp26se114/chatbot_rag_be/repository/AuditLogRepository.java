package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a
            FROM AuditLog a
            WHERE a.createdAt < :beforeTime
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    List<AuditLog> findRecentForDashboard(
            @Param("beforeTime") LocalDateTime beforeTime,
            Pageable pageable
    );

    @Query("""
            SELECT a
            FROM AuditLog a
            WHERE a.createdAt < :beforeTime
              AND a.action IN :types
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    List<AuditLog> findRecentForDashboardByActions(
            @Param("beforeTime") LocalDateTime beforeTime,
            @Param("types") List<String> types,
            Pageable pageable
    );
}
