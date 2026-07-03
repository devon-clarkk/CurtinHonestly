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

    @OneToOne(optional = false)
    @JoinColumn(name = "review_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Review review;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
