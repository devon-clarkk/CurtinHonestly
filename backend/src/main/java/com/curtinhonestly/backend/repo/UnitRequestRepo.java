package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.UnitRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitRequestRepo extends JpaRepository<UnitRequest, String> {
    List<UnitRequest> findAllByOrderByCreatedAtDesc();
}
