package com.curtinhonestly.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * A student club or study service (ComSSA, UniPASS, ...) that publishes study
 * sessions and events on the site through its own member accounts.
 *
 * {@code trusted} clubs publish immediately; events from other clubs wait in
 * PENDING for an admin. {@code active} false hides the club and all of its
 * events from the public site without deleting anything.
 *
 * Created by Hibernate ddl-auto (new table, no Flyway migration needed).
 */
@Entity
@Table(name = "clubs")
@Getter
@Setter
@NoArgsConstructor
public class Club {

    public static final int MAX_NAME = 120;
    public static final int MAX_SLUG = 60;
    public static final int MAX_DESCRIPTION = 600;
    public static final int MAX_URL = 500;
    public static final int MAX_EMAIL = 255;

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false, unique = true, length = MAX_NAME)
    private String name;

    /** Lower-case URL handle, e.g. "comssa". */
    @Column(nullable = false, unique = true, length = MAX_SLUG)
    private String slug;

    @Column(length = MAX_DESCRIPTION)
    private String description;

    @Column(name = "website_url", length = MAX_URL)
    private String websiteUrl;

    @Column(name = "logo_url", length = MAX_URL)
    private String logoUrl;

    @Column(name = "contact_email", length = MAX_EMAIL)
    private String contactEmail;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE NOT NULL")
    private boolean trusted = false;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE NOT NULL")
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();
}
