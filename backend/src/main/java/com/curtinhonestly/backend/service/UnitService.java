package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.CoursePrerequisiteOption;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.UnitPrerequisiteGroup;
import com.curtinhonestly.backend.domain.UnitPrerequisiteOption;
import com.curtinhonestly.backend.domain.UnitTuitionPattern;
import com.curtinhonestly.backend.dto.UnitCreateRequest;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.dto.UnitSummaryDTO;
import com.curtinhonestly.backend.mapper.UnitMapper;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitSpecification;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepo unitRepo;

    @Transactional(readOnly = true)
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
        return unitRepo.findByCode(code).orElseThrow(() -> new RuntimeException("Unit not found with code: " + code));
    }
    public Unit createUnit(UnitCreateRequest request)
    {
        try {
            log.info("Attempting to add/update unit: {}", request.code());

            Unit unit = new Unit();
            unit.setCode(request.code());
            unit.setName(request.name());
            unit.setDescription(request.description());
            unit.setUnitLink(request.unitLink());
            unit.setFaculty(request.faculty());
            unit.setLevel(request.level());
            unit.setArea(request.area());
            unit.setFieldOfEducation(request.fieldOfEducation());
            unit.setCredits(request.credits());
            unit.setContactHours(request.contactHours());
            unit.setResultType(request.resultType());

            // Check if unit with same code already exists to avoid unique constraint violations
            unitRepo.findByCode(unit.getCode()).ifPresent(existing -> {
                log.info("Unit with code {} already exists. Updating existing unit with ID: {}", unit.getCode(), existing.getId());
                unit.setId(existing.getId());
            });

            if (request.tuitionPatterns() != null) {
                unit.setTuitionPatterns(request.tuitionPatterns().stream().map(tp -> {
                    UnitTuitionPattern pattern = new UnitTuitionPattern();
                    pattern.setType(tp.type());
                    pattern.setDuration(tp.duration());
                    pattern.setUnit(unit);
                    return pattern;
                }).collect(Collectors.toList()));
            }

            if (request.prerequisiteGroups() != null) {
                unit.setPrerequisiteGroups(request.prerequisiteGroups().stream().map(g -> {
                    UnitPrerequisiteGroup group = new UnitPrerequisiteGroup();
                    group.setGroupName(g.groupName());
                    group.setRequirement(g.requirement());
                    group.setPosition(g.position());
                    group.setUnit(unit);
                    if (g.options() != null) {
                        group.setOptions(g.options().stream().map(o -> {
                            UnitPrerequisiteOption option = new UnitPrerequisiteOption();
                            option.setCode(o.code());
                            option.setTitle(o.title());
                            option.setConcurrent(o.concurrent());
                            option.setGroup(group);
                            return option;
                        }).collect(Collectors.toList()));
                    }
                    if (g.courseOptions() != null) {
                        group.setCourseOptions(g.courseOptions().stream().map(o -> {
                            CoursePrerequisiteOption option = new CoursePrerequisiteOption();
                            option.setCourseCode(o.courseCode());
                            option.setCredits(o.credits());
                            option.setTitle(o.title());
                            option.setConcurrent(o.concurrent());
                            option.setGroup(group);
                            return option;
                        }).collect(Collectors.toList()));
                    }
                    return group;
                }).collect(Collectors.toList()));
            }

            Unit savedUnit = unitRepo.save(unit);
            log.info("Successfully saved unit: {} with ID: {}", savedUnit.getCode(), savedUnit.getId());
            return savedUnit;
        } catch (Exception e) {
            log.error("Failed to create unit {}: {}", request.code(), e.getMessage(), e);
            throw e;
        }
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
