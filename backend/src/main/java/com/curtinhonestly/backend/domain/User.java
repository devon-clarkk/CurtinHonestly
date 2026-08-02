package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Entity

// Map to the "app_users" table to avoid conflicts with reserved words
@Table(name = "app_users")

// Lombok getters/setters
@Getter
@Setter

// Lombok constructors
@NoArgsConstructor
@AllArgsConstructor

// Json setup
@JsonInclude(JsonInclude.Include.NON_DEFAULT)

// User class - Holds data for CurtinHonestly users.

public class User {

    @Id
    @UuidGenerator
    private String id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    private List<UserRole> roles = new ArrayList<>();

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean verifiedStudent = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean banned = false;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();

    // No REMOVE cascade: deleting a user must not delete their reviews (see
    // UserService.deleteAccount) — reviews are detached (anonymized) by default,
    // and the app deletes them explicitly only when the user opts into full removal.
    @OneToMany(mappedBy = "user")
    private List<Review> reviews;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(length = 100)
    private String registeredViaRef;

    // Self-reported completed unit codes, used by the prerequisite checker
    // (roadmap 4.4) to evaluate a unit's UnitPrerequisiteGroup/Option data.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_completed_units", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "unit_code", length = 30)
    private Set<String> completedUnitCodes = new HashSet<>();

}





