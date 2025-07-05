package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;

@Entity

// Map to the "units" table
@Table(name = "units")

// Lombok getters/setters
@Getter
@Setter

// Lombok constructors
@NoArgsConstructor
@AllArgsConstructor

// Json setup
//@JsonInclude(JsonInclude.Include.NON_DEFAULT)

// Unit class - Stores data for CurtinHonestly units.
public class Unit {
    @Id
    @UuidGenerator
    @Column(name = "id", unique = true, updatable = false)
    private String id;
    private String code;
    private String name;
    private String description;
    private String faculty;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("unit") // Ignores the unit inside each review
    private List<Review> reviews;
}
