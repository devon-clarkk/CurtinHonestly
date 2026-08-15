package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "campaign_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class CampaignEntry {

    @Id
    @UuidGenerator
    private String id;

    @Column(unique = true, nullable = false, length = 16)
    private String entryToken;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    // The unit whose qualifying review triggered this entry. Kept even if the
    // review itself is later deleted, so the per-unit re-earn gate in
    // CampaignService still holds after a delete-and-resubmit cycle.
    @ManyToOne(optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    // ManyToOne, not OneToOne: one review can now back an entry in EACH campaign the
    // user is enrolled in, so review_id is no longer unique. Duplicate entries within
    // a single campaign are still prevented by the per-(campaign,user,unit) gate.
    // Nullable + SET_NULL: deleting the review must not delete the entry (the user
    // keeps a token they already earned), only detach it from the review.
    @ManyToOne
    @JoinColumn(name = "review_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Review review;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
