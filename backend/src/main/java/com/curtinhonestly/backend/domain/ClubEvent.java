package com.curtinhonestly.backend.domain;

import com.curtinhonestly.backend.util.UnitTargetRule;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * A study session, workshop or social run by a {@link Club}.
 *
 * Times are stored as UTC instants and rendered in Australia/Perth by the
 * frontend. Recurrence is a free-text note ("Every Tuesday, weeks 2 to 12")
 * plus the {@code recurring} flag; there is no RRULE engine. A recurring event
 * keeps showing while it is PUBLISHED even after its first start has passed.
 *
 * Unit targeting has the same shape as {@link UnitResourceLink}: a specific
 * unit, or a rule over code prefixes, faculty and level (see
 * {@link UnitTargetRule}). {@code showOnHome} puts it on the home page strip.
 *
 * Created by Hibernate ddl-auto (new table, no Flyway migration needed).
 */
@Entity
@Table(name = "club_events", indexes = {
    @Index(name = "idx_club_events_status_starts", columnList = "status, starts_at"),
    @Index(name = "idx_club_events_target_unit", columnList = "target_unit_id"),
    @Index(name = "idx_club_events_club", columnList = "club_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClubEvent {

    public static final int MAX_TITLE = 140;
    public static final int MAX_DESCRIPTION = 2000;
    public static final int MAX_LOCATION = 200;
    public static final int MAX_LINK = 500;
    public static final int MAX_RECURRENCE_NOTE = 120;
    public static final int MAX_PREFIXES = 200;
    public static final int MAX_REJECTION_REASON = 300;

    @Id
    @UuidGenerator
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Club club;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(columnDefinition = "TEXT", length = MAX_DESCRIPTION)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30) DEFAULT 'OTHER' NOT NULL")
    private ClubEventKind kind = ClubEventKind.OTHER;

    @Column(name = "starts_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant startsAt = Instant.now();

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(length = MAX_LOCATION)
    private String location;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean online = false;

    /** Registration, Teams or Discord link. Null when there is none. */
    @Column(length = MAX_LINK)
    private String link;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean recurring = false;

    @Column(name = "recurrence_note", length = MAX_RECURRENCE_NOTE)
    private String recurrenceNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_unit_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Unit targetUnit;

    /** Comma separated, upper-case, trimmed unit-code prefixes, e.g. "COMP1,ISAD1". Null when not a criterion. */
    @Column(name = "code_prefixes", length = MAX_PREFIXES)
    private String codePrefixes;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Faculty faculty;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UnitLevel level;

    @Column(name = "show_on_home", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean showOnHome = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'DRAFT' NOT NULL")
    private ClubEventStatus status = ClubEventStatus.DRAFT;

    /** Why an admin rejected it, shown back to the club. Cleared on the next publish. */
    @Column(name = "rejection_reason", length = MAX_REJECTION_REASON)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant updatedAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "view_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0 NOT NULL")
    private int viewCount = 0;

    /** The targeting rule this row expresses, detached from the entity. */
    @Transient
    public UnitTargetRule rule() {
        return UnitTargetRule.fromColumns(targetUnit == null ? null : targetUnit.getId(), codePrefixes, faculty, level);
    }
}
