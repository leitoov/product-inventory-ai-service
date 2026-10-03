package com.inventory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sales_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesSessionEntity {

    @Id
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "cart_json", columnDefinition = "TEXT")
    private String cartJson;

    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
