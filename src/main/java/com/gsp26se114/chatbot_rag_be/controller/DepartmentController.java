package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.CreateDepartmentRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateDepartmentRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.DepartmentResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenant-admin/departments")
@RequiredArgsConstructor
@Tag(name = "13. 🏢 Tenant Admin - Department Management", description = "Quản lý phòng ban trong tenant (TENANT_ADMIN)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class DepartmentController {
    
    private final DepartmentService departmentService;
    
    /**
     * Get all departments in tenant
     */
    @GetMapping
    @Operation(summary = "Lấy danh sách phòng ban", 
               description = "Lấy tất cả phòng ban trong tenant (bao gồm cả inactive)")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<DepartmentResponse> departments = departmentService.getAllDepartments(userDetails.getUsername());
        return ResponseEntity.ok(departments);
    }
    
    /**
     * Get active departments only
     */
    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách phòng ban đang hoạt động", 
               description = "Lấy các phòng ban đang active trong tenant")
    public ResponseEntity<List<DepartmentResponse>> getActiveDepartments(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<DepartmentResponse> departments = departmentService.getActiveDepartments(userDetails.getUsername());
        return ResponseEntity.ok(departments);
    }
    
    /**
     * Get department by ID
     */
    @GetMapping("/{departmentId}")
    @Operation(summary = "Xem chi tiết phòng ban", 
               description = "Lấy thông tin chi tiết của một phòng ban")
    public ResponseEntity<DepartmentResponse> getDepartmentById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer departmentId) {
        DepartmentResponse department = departmentService.getDepartmentById(
                userDetails.getUsername(), departmentId);
        return ResponseEntity.ok(department);
    }
    
    /**
     * Create new department
     */
    @PostMapping
    @Operation(summary = "Tạo phòng ban mới", 
               description = "Tạo phòng ban mới trong tenant (code phải unique trong tenant)")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse department = departmentService.createDepartment(
                userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(department);
    }
    
    /**
     * Update department
     */
    @PutMapping("/{departmentId}")
    @Operation(summary = "Cập nhật thông tin phòng ban", 
               description = "Cập nhật code, name, description, parent department, hoặc trạng thái active")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        DepartmentResponse department = departmentService.updateDepartment(
                userDetails.getUsername(), departmentId, request);
        return ResponseEntity.ok(department);
    }
    
    /**
     * Delete department (soft delete)
     */
    @DeleteMapping("/{departmentId}")
    @Operation(summary = "Xóa phòng ban", 
               description = "Soft delete phòng ban (set isActive = false). Không thể xóa nếu có nhân viên hoặc phòng ban con.")
    public ResponseEntity<MessageResponse> deleteDepartment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer departmentId) {
        departmentService.deleteDepartment(userDetails.getUsername(), departmentId);
        return ResponseEntity.ok(new MessageResponse("Phòng ban đã được xóa thành công!"));
    }
}
