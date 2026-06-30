package com.curtinhonestly.backend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "course_prerequisite_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoursePrerequisiteOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private UnitPrerequisiteGroup group;

    @Column(name = "course_code", length = 50)
    private String courseCode;

    private Integer credits;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false)
    private boolean concurrent = false;
}
