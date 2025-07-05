package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.dto.UnitSummaryDTO;
import com.curtinhonestly.backend.repo.UnitRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepo unitRepo;

    public UnitSummaryDTO toSummaryDTO(Unit unit)
    {
        UnitSummaryDTO dto = new UnitSummaryDTO();
        dto.setName(unit.getName());
        dto.setCode(unit.getCode());
        dto.setFaculty(unit.getFaculty());


        // Get reviews to calculate Unit stats.
        List<Review> reviews = unit.getReviews();

        // Get the number of reviews
        dto.setNumberOfReviews(reviews.size());

        // Calculate average rating and % of students who would take the unit again from the unit's reviews
        if (!reviews.isEmpty()) {
            double averageRating = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0);
            dto.setAverageRating(averageRating);

            long positiveCount = reviews.stream()
                    .filter(Review::isWouldTakeAgain)
                    .count();

            double percentage = Math.round((positiveCount * 100.0 / reviews.size()) * 10.0) / 10.0;
            dto.setWouldTakeAgainPercentage(percentage);
        } else {
            dto.setAverageRating(0);
            dto.setWouldTakeAgainPercentage(0.0);
        }


        return dto;
    }


    public Page<Unit> getAllUnits(int page, int size)
    {
        return unitRepo.findAll(PageRequest.of(page, size, Sort.by("name")));
    }

    public Unit getUnitById(String id) throws RuntimeException
    {
        return unitRepo.findById(id).orElseThrow(() -> new RuntimeException("Unit not found"));
    }

    public Unit getUnitByCode(String code) throws RuntimeException
    {
        return unitRepo.findById(code).orElseThrow(() -> new RuntimeException("Unit not found"));
    }
    public Unit createUnit(Unit unit)
    {
        log.info("Unit added");
        return unitRepo.save(unit);
    }
    public void deleteUnit(Unit unit)
    {
        log.info("Unit deleted");
        unitRepo.delete(unit);
    }
    public void deleteUnitById(String id) {
        Unit unit = getUnitById(id);
        deleteUnit(unit);
    }

}
