package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A shareable link that enrols whoever signs up through it into one OR MORE
 * campaigns at once (e.g. "leave one review" + "one entry per review" draws under
 * a single link). Distinct from a Campaign: a campaign holds the reward rules, a
 * ReferralLink is the distribution handle that bundles campaigns and tracks
 * visits. A link with no campaigns is a pure tracking link.
 */
@Entity
@Table(name = "referral_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ReferralLink {

    @Id
    @UuidGenerator
    private String id;

    // The ?ref= value. Shares the resolution namespace with campaign slugs/codes,
    // so creation rejects a slug that collides with either (see ReferralLinkService).
    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    // Site-relative path the link forwards to (e.g. "/", "/register").
    @Column(length = 200)
    private String landingPath;

    @Column(nullable = false)
    private boolean active = true;

    // Attributed visits, incremented by the site-wide ?ref= capture.
    @Column(nullable = false)
    private long visitCount = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "referral_link_campaigns",
            joinColumns = @JoinColumn(name = "link_id"),
            inverseJoinColumns = @JoinColumn(name = "campaign_id"))
    private Set<Campaign> campaigns = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
