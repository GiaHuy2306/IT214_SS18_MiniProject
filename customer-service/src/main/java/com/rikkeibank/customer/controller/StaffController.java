package com.rikkeibank.customer.controller;

import com.rikkeibank.common.dto.ApiResponse;
import com.rikkeibank.common.dto.StaffDto;
import com.rikkeibank.customer.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffDto>>> getAllStaff() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getAllStaff()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffDto>> getStaffById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getStaffById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody StaffDto dto) {
        if (role != null && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required"));
        }
        StaffDto created = staffService.createStaff(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Staff created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffDto>> updateStaff(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("id") Long id,
            @Valid @RequestBody StaffDto dto) {
        if (role != null && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required"));
        }
        StaffDto updated = staffService.updateStaff(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Staff updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("id") Long id) {
        if (role != null && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required"));
        }
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.success("Staff deleted successfully", null));
    }
}
