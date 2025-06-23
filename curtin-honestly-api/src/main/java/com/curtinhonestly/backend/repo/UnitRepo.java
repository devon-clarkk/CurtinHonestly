package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnitRepo extends JpaRepository<Unit, String> {
    Optional<Unit> findById(String id);

}
