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

    // Credential-change cut-off: JWTs issued before this instant are rejected by
    // JwtAuthenticationFilter. Stamped on password reset and email change so those
    // actions actually end existing sessions instead of leaving a stolen 7-day
    // token alive (security audit finding #4).
    //
    // Nullable on purpose - NULL means "no cut-off yet", which is what every
    // pre-existing account is. Always stamp it truncated to whole seconds: a JWT's
    // `iat` has second precision, so a nanosecond-precision stamp would be strictly
    // newer than a token minted in the same instant and would log the user straight
    // back out. See V5__app_users_tokens_valid_after.sql.
    @Column(name = "tokens_valid_after", columnDefinition = "TIMESTAMPTZ")
    private Instant tokensValidAfter;

    // No REMOVE cascade: deleting a user must not delete their reviews (see
    // UserService.deleteAccount) — reviews are detached (anonymized) by default,
    // and the app deletes them explicitly only when the user opts into full removal.
    @OneToMany(mappedBy = "user")
    private List<Review> reviews;

    // A user can be enrolled in several campaigns at once (e.g. two draws under one
    // referral link). Entries accrue independently per campaign. The join table is
    // created + backfilled from the old single campaign_id column by Flyway V6; the
    // orphaned campaign_id column is left in place (ddl-auto never drops columns).
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_campaigns",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "campaign_id"))
    private Set<Campaign> campaigns = new HashSet<>();

    @Column(length = 100)
    private String registeredViaRef;

    // Self-reported completed unit codes, used by the prerequisite checker
    // (roadmap 4.4) to evaluate a unit's UnitPrerequisiteGroup/Option data.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_completed_units", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "unit_code", length = 30)
    private Set<String> completedUnitCodes = new HashSet<>();

}





