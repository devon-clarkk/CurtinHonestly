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

// Map to the "app_users" table to avoid conflicts with reserved words
@Table(name = "app_users")

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
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    private List<UserRole> roles = new ArrayList<>();

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    private String username;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Review> reviews;

}





