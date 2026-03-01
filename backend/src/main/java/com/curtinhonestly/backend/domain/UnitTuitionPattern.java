package com.curtinhonestly.backend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "unit_tuition_patterns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitTuitionPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(length = 50)
    private String type;

    @Column(length = 100)
    private String duration;
}
