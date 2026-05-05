package com.hsmart.backend.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 150)
    private String fullName;

    @Column(length = 30)
    private String phoneNumber;

    @Column(columnDefinition = "text")
    private String address;

    @Column(columnDefinition = "text")
    private String avatarUrl;

    @Builder.Default
    @Column(name = "trust_score", precision = 4, scale = 2)
    private BigDecimal trustScore = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "review_count")
    private Long reviewCount = 0L;

    @PrePersist
    public void applyDefaults() {
        if (role == null) {
            role = Role.USER;
        }
        if (trustScore == null) {
            trustScore = BigDecimal.ZERO;
        }
        if (reviewCount == null) {
            reviewCount = 0L;
        }
    }
}
