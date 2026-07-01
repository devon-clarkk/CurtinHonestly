package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "unit_prerequisite_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitPrerequisiteGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "unit_code", referencedColumnName = "code", nullable = false)
    private Unit unit;

    @Column(name = "group_name", length = 50)
    private String groupName;

    @Column(length = 10)
    private String requirement;

    private Integer position;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("group")
    private List<UnitPrerequisiteOption> options;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("group")
    private List<CoursePrerequisiteOption> courseOptions;
}
