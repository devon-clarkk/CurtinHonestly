package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.UnitTip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitTipRepo extends JpaRepository<UnitTip, String> {
    Optional<UnitTip> findById(String id);
    List<UnitTip> findByUnit_IdOrderByCreatedAtDesc(String unitId);
    List<UnitTip> findByUser_IdOrderByCreatedAtDesc(String userId);
}
