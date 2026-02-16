package com.gsp26se114.chatbot_rag_be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity(name = "blacklisted_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistedToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;
}