package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.UnitRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UnitRequestRepo extends JpaRepository<UnitRequest, String> {
    List<UnitRequest> findAllByOrderByCreatedAtDesc();

    // Rows are [requestedCode, count]. Codes are grouped exactly as stored; the
    // admin service folds case variants together before ranking them.
    @Query("select ur.requestedCode, count(ur) from UnitRequest ur group by ur.requestedCode")
    List<Object[]> countGroupedByRequestedCode();
}
