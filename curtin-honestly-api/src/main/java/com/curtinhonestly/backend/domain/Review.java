package com.curtinhonestly.backend.domain;


import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity

// Map to the "reviews" table
@Table(name = "reviews")

// Lombok getters/setters
@Getter
@Setter

// Lombok constructors
@NoArgsConstructor
@AllArgsConstructor

// Json setup
@JsonInclude(JsonInclude.Include.NON_DEFAULT)

// Review class - Holds data for CurtinHonestly reviews.
public class Review {
    @Id
    @UuidGenerator
    private String id;

    private int rating; // 0-10
    private Integer finalGrade; // Optional.
    private String reviewText;
    private String semesterTaken; // Optional
    private String professor; // Optional

    private int workload; // 0-10
    private boolean hasExam; // Optional
    private boolean wouldTakeAgain;

    @ManyToOne
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;



}
