package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * A student-submitted "we don't have this unit yet" signal, captured from the
 * catalog's empty search state. Public/unauthenticated by design — the raw
 * list of requested codes drives what to import next (catalog-and-growth.md #1).
 */
@Entity
@Table(name = "unit_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class UnitRequest {

    @Id
    @UuidGenerator
    private String id;

    @Column(nullable = false, length = 100)
    private String requestedCode;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW() NOT NULL")
    private Instant createdAt = Instant.now();
}
