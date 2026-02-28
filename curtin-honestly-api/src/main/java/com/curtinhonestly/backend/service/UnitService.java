package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.dto.UnitSummaryDTO;
import com.curtinhonestly.backend.mapper.UnitMapper;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitSpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepo unitRepo;

    public Page<UnitSummaryDTO> getAllUnits(int page, int size, String search, List<Faculty> faculties, UnitLevel level, String sortBy) {
        Sort sort = Sort.by(Sort.Direction.ASC, "code"); // Default sort

        if (sortBy != null && !sortBy.isEmpty()) {
            switch (sortBy) {
                case "name":
                    sort = Sort.by(Sort.Direction.ASC, "name");
                    break;
                case "name_desc":
                    sort = Sort.by(Sort.Direction.DESC, "name");
                    break;
                case "code_desc":
                    sort = Sort.by(Sort.Direction.DESC, "code");
                    break;
                case "most_reviewed":
                    sort = Sort.by(Sort.Direction.DESC, "reviewCount");
                    break;
                case "least_reviewed":
                    sort = Sort.by(Sort.Direction.ASC, "reviewCount");
                    break;
                case "highest_rated":
                    sort = Sort.by(Sort.Direction.DESC, "averageRating");
                    break;
                case "lowest_rated":
                    sort = Sort.by(Sort.Direction.ASC, "averageRating");
                    break;
                case "highest_mark":
                    sort = Sort.by(Sort.Direction.DESC, "averageFinalGrade");
                    break;
                case "lowest_mark":
                    sort = Sort.by(Sort.Direction.ASC, "averageFinalGrade");
                    break;
                case "lowest_workload":
                    sort = Sort.by(Sort.Direction.ASC, "averageWorkload");
                    break;
                case "highest_workload":
                    sort = Sort.by(Sort.Direction.DESC, "averageWorkload");
                    break;
            }
        }

        Specification<Unit> spec = UnitSpecification.filterUnits(search, faculties, level);
        Page<Unit> units = unitRepo.findAll(spec, PageRequest.of(page, size, sort));
        return units.map(UnitMapper::toSummaryDTO);
    }

    public Unit getUnitById(String id) throws RuntimeException
    {
        return unitRepo.findById(id).orElseThrow(() -> new RuntimeException("Unit not found"));
    }
    public UnitDetailsDTO getUnitDetailsDTOByCode(String code) throws RuntimeException {
        Unit unit = unitRepo.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Unit not found"));

        return UnitMapper.toDetailsDTO(unit);
    }
    public Unit getUnitByCode(String code) throws RuntimeException
    {
        return unitRepo.findById(code).orElseThrow(() -> new RuntimeException("Unit not found"));
    }
    public Unit createUnit(Unit unit)
    {
        if (unit.getTuitionPatterns() != null) {
            unit.getTuitionPatterns().forEach(pattern -> pattern.setUnit(unit));
        }

        if (unit.getPrerequisiteGroups() != null) {
            unit.getPrerequisiteGroups().forEach(group -> {
                group.setUnit(unit);
                if (group.getOptions() != null) {
                    group.getOptions().forEach(option -> option.setGroup(group));
                }
            });
        }

        log.info("Unit added: {}", unit.getCode());
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
