package com.curtinhonestly.backend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "unit_prerequisite_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitPrerequisiteOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private UnitPrerequisiteGroup group;

    @Column(length = 30)
    private String code;

    @Column(length = 255)
    private String title;
}
