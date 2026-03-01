package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;

@Entity
@Table(name = "units")
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

/*
    @Formula("(SELECT COUNT(*) FROM reviews r WHERE r.unit_id = id)")
    private Integer reviewCount;

    @Formula("(SELECT COALESCE(AVG(r.rating), 0) FROM reviews r WHERE r.unit_id = id)")
    private Double averageRating;

    @Formula("(SELECT COALESCE(AVG(r.workload), 0) FROM reviews r WHERE r.unit_id = id)")
    private Double averageWorkload;

    @Formula("(SELECT COALESCE(AVG(r.final_grade), 0) FROM reviews r WHERE r.unit_id = id)")
    private Double averageFinalGrade;
*/

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
    @JsonIgnoreProperties("unit") // Ignores the unit inside each review
    private List<Review> reviews;
}
