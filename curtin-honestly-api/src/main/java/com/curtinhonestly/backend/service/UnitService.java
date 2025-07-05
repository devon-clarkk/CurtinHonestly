package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Unit;
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
