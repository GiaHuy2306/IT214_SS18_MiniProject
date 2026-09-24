package com.rikkeibank.customer.service;

import com.rikkeibank.common.dto.StaffDto;
import com.rikkeibank.customer.entity.Staff;
import com.rikkeibank.customer.repository.StaffRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;

    @PostConstruct
    public void initDefaultStaff() {
        if (staffRepository.count() == 0) {
            log.info("Initializing sample staff/teller records");
            staffRepository.save(Staff.builder()
                    .staffCode("STAFF001")
                    .fullName("Le Thi Giao Dich Vien")
                    .email("teller@rikkeibank.com")
                    .phoneNumber("0987654321")
                    .department("Counter Operations")
                    .branchCode("HANOI_MAIN")
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    public List<StaffDto> getAllStaff() {
        return staffRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public StaffDto getStaffById(Long id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found with ID: " + id));
        return mapToDto(staff);
    }

    @Transactional
    public StaffDto createStaff(StaffDto dto) {
        if (staffRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Staff email already exists: " + dto.getEmail());
        }

        String code = dto.getStaffCode() != null && !dto.getStaffCode().isBlank()
                ? dto.getStaffCode()
                : "STF" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Staff staff = Staff.builder()
                .staffCode(code)
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .phoneNumber(dto.getPhoneNumber())
                .department(dto.getDepartment() != null ? dto.getDepartment() : "Retail Banking")
                .branchCode(dto.getBranchCode() != null ? dto.getBranchCode() : "MAIN_BRANCH")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        return mapToDto(staffRepository.save(staff));
    }

    @Transactional
    public StaffDto updateStaff(Long id, StaffDto dto) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found with ID: " + id));

        staff.setFullName(dto.getFullName());
        staff.setPhoneNumber(dto.getPhoneNumber());
        if (dto.getDepartment() != null) staff.setDepartment(dto.getDepartment());
        if (dto.getBranchCode() != null) staff.setBranchCode(dto.getBranchCode());
        if (dto.getStatus() != null) staff.setStatus(dto.getStatus());

        return mapToDto(staffRepository.save(staff));
    }

    @Transactional
    public void deleteStaff(Long id) {
        if (!staffRepository.existsById(id)) {
            throw new IllegalArgumentException("Staff not found with ID: " + id);
        }
        staffRepository.deleteById(id);
    }

    private StaffDto mapToDto(Staff s) {
        return StaffDto.builder()
                .id(s.getId())
                .staffCode(s.getStaffCode())
                .fullName(s.getFullName())
                .email(s.getEmail())
                .phoneNumber(s.getPhoneNumber())
                .department(s.getDepartment())
                .branchCode(s.getBranchCode())
                .status(s.getStatus())
                .build();
    }
}
