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

    // Review must have received at least this many likes to count as qualifying (0 = no requirement).
    @Column(nullable = false)
    private int minLikesReceived = 0;

    // User must have liked at least this many reviews within the campaign window before earning entries (0 = no requirement).
    @Column(nullable = false)
    private int minLikesGiven = 0;

    // Tracking-only referral links: no prize, no requirement, no draw entries.
    // The link just forwards to the app and attributes downstream visits/signups/
    // reviews. All the reward fields above are ignored when this is true.
    // columnDefinition gives a DB-side default so ddl-auto=update can add this
    // NOT NULL column to an already-populated campaigns table (see User.banned).
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean trackingOnly = false;

    // Attributed visits recorded when someone opens the referral link. Incremented
    // atomically via CampaignRepo.incrementVisitCountBySlug.
    @Column(nullable = false, columnDefinition = "BIGINT DEFAULT 0 NOT NULL")
    private long visitCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
