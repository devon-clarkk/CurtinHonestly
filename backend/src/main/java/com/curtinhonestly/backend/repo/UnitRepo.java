package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepo extends JpaRepository<Unit, String>, JpaSpecificationExecutor<Unit> {
    Optional<Unit> findById(String id);
    Optional<Unit> findByCode(String code);

    // Catalogue size per faculty for the admin coverage chart. Rows are [Faculty, count].
    @Query("select u.faculty, count(u) from Unit u group by u.faculty")
    List<Object[]> countGroupedByFaculty();
}
