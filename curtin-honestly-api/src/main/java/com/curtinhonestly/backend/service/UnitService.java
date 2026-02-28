package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.dto.UnitSummaryDTO;
import com.curtinhonestly.backend.mapper.UnitMapper;
import com.curtinhonestly.backend.repo.UnitRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Transactional(rollbackOn = Exception.class)
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepo unitRepo;

    public Page<UnitSummaryDTO> getAllUnits(int page, int size) {
        Page<Unit> units = unitRepo.findAll(PageRequest.of(page, size));
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
