package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;


@Entity

// Map to the "users" table
@Table(name = "users")

// Lombok getters/setters
@Getter
@Setter

// Lombok constructors
@NoArgsConstructor
@AllArgsConstructor

// Json setup
@JsonInclude(JsonInclude.Include.NON_DEFAULT)

// User class - Holds data for CurtinHonestly users.

public class User {

    @Id
    @UuidGenerator
    private String id;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private List<UserRole> roles = new ArrayList<>();

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String username;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Review> reviews;

}





