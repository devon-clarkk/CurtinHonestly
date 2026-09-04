package com.curtinhonestly.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * A curated link shown on unit pages: a Discord server, a club, a textbook, a
 * past-paper archive, a YouTube playlist and so on.
 *
 * A row targets units in one of two ways:
 * <ul>
 *   <li>{@code targetUnit} set: the link belongs to exactly that unit.</li>
 *   <li>{@code targetUnit} null: the link is a rule. Every non-null criterion
 *       ({@code codePrefixes}, {@code faculty}, {@code level}) must match a unit
 *       for the link to appear on it. A rule with no criteria matches every
 *       unit, which is how a site-wide link is expressed.</li>
 * </ul>
 * So the ComSSA Discord is a single row with prefixes such as
 * {@code COMP,ISAD,ISEC,CNCO,CMPE} rather than one row per unit.
 *
 * Created by Hibernate ddl-auto (new table, no Flyway migration needed).
 * Deleting the target unit deletes its links; deleting the submitting user
 * keeps the link and nulls the reference.
 */
@Entity
@Table(name = "unit_resource_links", indexes = {
    @Index(name = "idx_unit_resource_links_status", columnList = "status"),
    @Index(name = "idx_unit_resource_links_target_unit", columnList = "target_unit_id")
})
@Getter
@Setter
@NoArgsConstructor
public class UnitResourceLink {

    public static final int MAX_TITLE = 120;
    public static final int MAX_URL = 500;
    public static final int MAX_DESCRIPTION = 300;
    public static final int MAX_NOTE = 300;
    public static final int MAX_PREFIXES = 200;

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Column(nullable = false, length = MAX_URL)
    private String url;

    @Column(length = MAX_DESCRIPTION)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30) DEFAULT 'OTHER' NOT NULL")
    private ResourceKind kind = ResourceKind.OTHER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_unit_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Unit targetUnit;

    /** Comma separated, upper-case, trimmed unit-code prefixes, e.g. "COMP,ISAD". Null when not a criterion. */
    @Column(name = "code_prefixes", length = MAX_PREFIXES)
    private String codePrefixes;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Faculty faculty;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UnitLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING' NOT NULL")
    private ResourceStatus status = ResourceStatus.PENDING;

    @Column(name = "sort_order", nullable = false, columnDefinition = "INTEGER DEFAULT 0 NOT NULL")
    private int sortOrder = 0;

    @Column(name = "click_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0 NOT NULL")
    private int clickCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User submittedBy;

    @Column(name = "submitter_note", length = MAX_NOTE)
    private String submitterNote;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** The stored prefix list split back out; empty when prefixes are not a criterion. */
    @Transient
    public List<String> prefixList() {
        return splitPrefixes(codePrefixes);
    }

    /** Splits and normalises a raw prefix string: trimmed, upper-cased, blanks dropped, duplicates removed. */
    public static List<String> splitPrefixes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }
}
