package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "departments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Integer id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 50)
    private String code; // HR, DEV, SALES, FINANCE

    @Column(nullable = false, length = 255)
    private String name; // Phòng Nhân Sự, Phòng Phát Triển

    private String description;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;
}
