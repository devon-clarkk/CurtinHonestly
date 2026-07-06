package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "units", indexes = {
    @Index(name = "idx_units_faculty_level_code", columnList = "faculty, level, code"),
    @Index(name = "idx_units_review_count", columnList = "review_count"),
    @Index(name = "idx_units_average_rating", columnList = "average_rating")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Unit {
    @Id
    @UuidGenerator
    @Column(name = "id", unique = true, updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT", length = 5000)
    private String description;

    @Column(name = "unit_link", length = 500)
    private String unitLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Faculty faculty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitLevel level;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(name = "average_rating", nullable = false)
    private double averageRating = 0;

    @Column(name = "average_workload", nullable = false)
    private double averageWorkload = 0;

    @Column(name = "average_final_grade", nullable = false)
    private double averageFinalGrade = 0;

    @Column(name = "would_take_again_ratio", nullable = false)
    private double wouldTakeAgainRatio = 0;

    @Column(name = "latest_review_at")
    private Instant latestReviewAt;

    @Column(length = 255)
    private String area;

    @Column(name = "field_of_education", length = 100)
    private String fieldOfEducation;

    private Integer credits;

    @Column(name = "contact_hours")
    private Integer contactHours;

    @Column(name = "result_type", length = 50)
    private String resultType;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("unit")
    private List<UnitTuitionPattern> tuitionPatterns;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("unit")
    private List<UnitPrerequisiteGroup> prerequisiteGroups;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("unit")
    @BatchSize(size = 30)
    private List<Review> reviews;
}
