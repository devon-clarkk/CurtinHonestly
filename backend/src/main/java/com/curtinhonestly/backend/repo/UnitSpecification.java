package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UnitSpecification {

    public static Specification<Unit> filterUnits(String search, List<Faculty> faculties, UnitLevel level) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isEmpty()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                Predicate codePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), searchPattern);
                Predicate namePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), searchPattern);
                predicates.add(criteriaBuilder.or(codePredicate, namePredicate));
            }

            if (faculties != null && !faculties.isEmpty()) {
                predicates.add(root.get("faculty").in(faculties));
            }

            if (level != null) {
                predicates.add(criteriaBuilder.equal(root.get("level"), level));
            }

            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
