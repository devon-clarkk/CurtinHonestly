package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class Campaign {

    @Id
    @UuidGenerator
    private String id;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String prizeDescription;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active = true;

    private Integer maxRedemptions;

    @Column(nullable = false)
    private int minReviewLength = 50;

    @Column(nullable = false)
    private int maxEntriesPerUser = 5;

    @Column(nullable = false)
    private boolean requireVerifiedStudent = true;

    @Column(nullable = false)
    private int requiredReviewCount = 1;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
