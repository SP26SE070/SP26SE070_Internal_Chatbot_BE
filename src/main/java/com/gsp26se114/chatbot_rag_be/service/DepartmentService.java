package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.Department;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.payload.request.CreateDepartmentRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateDepartmentRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.DepartmentResponse;
import com.gsp26se114.chatbot_rag_be.repository.DepartmentRepository;
import com.gsp26se114.chatbot_rag_be.repository.UserRepository;
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
public class DepartmentService {
    
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    
    /**
     * Get all departments for a tenant
     */
    public List<DepartmentResponse> getAllDepartments(String tenantAdminEmail) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        
        log.info("Fetching all departments for tenant: {}", tenantId);
        List<Department> departments = departmentRepository.findByTenantId(tenantId);
        
        return departments.stream()
                .map(this::mapToDepartmentResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get active departments only
     */
    public List<DepartmentResponse> getActiveDepartments(String tenantAdminEmail) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        
        log.info("Fetching active departments for tenant: {}", tenantId);
        List<Department> departments = departmentRepository.findByTenantIdAndIsActive(tenantId, true);
        
        return departments.stream()
                .map(this::mapToDepartmentResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get department by ID (same tenant only)
     */
    public DepartmentResponse getDepartmentById(String tenantAdminEmail, Integer departmentId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Phòng ban không tồn tại với ID: " + departmentId));
        
        // Verify same tenant
        if (!department.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Bạn không có quyền truy cập phòng ban này!");
        }
        
        return mapToDepartmentResponse(department);
    }
    
    /**
     * Create new department
     */
    @Transactional
    public DepartmentResponse createDepartment(String tenantAdminEmail, CreateDepartmentRequest request) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        
        log.info("Creating new department in tenant: {}", tenantId);
        
        // Check if department code already exists in tenant
        if (departmentRepository.existsByTenantIdAndCode(tenantId, request.code())) {
            throw new RuntimeException("Mã phòng ban đã tồn tại trong tenant!");
        }
        
        // Create department
        Department department = new Department();
        department.setTenantId(tenantId);
        department.setCode(request.code().toUpperCase());
        department.setName(request.name());
        department.setDescription(request.description());
        department.setIsActive(true);
        department.setCreatedAt(LocalDateTime.now());
        
        Department savedDepartment = departmentRepository.save(department);
        log.info("Created department: {} (ID: {})", savedDepartment.getName(), savedDepartment.getId());
        
        return mapToDepartmentResponse(savedDepartment);
    }
    
    /**
     * Update department
     */
    @Transactional
    public DepartmentResponse updateDepartment(String tenantAdminEmail, Integer departmentId, UpdateDepartmentRequest request) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Phòng ban không tồn tại với ID: " + departmentId));
        
        // Verify same tenant
        if (!department.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa phòng ban này!");
        }
        
        log.info("Updating department: {} (ID: {})", department.getName(), departmentId);
        
        // Update code if provided and different
        if (request.code() != null && !request.code().equals(department.getCode())) {
            // Check if new code already exists
            if (departmentRepository.existsByTenantIdAndCode(tenantId, request.code())) {
                throw new RuntimeException("Mã phòng ban đã tồn tại trong tenant!");
            }
            department.setCode(request.code().toUpperCase());
        }
        
        // Update name if provided
        if (request.name() != null) {
            department.setName(request.name());
        }
        
        // Update description if provided
        if (request.description() != null) {
            department.setDescription(request.description());
        }
        
        // Update isActive if provided
        if (request.isActive() != null) {
            department.setIsActive(request.isActive());
        }
        
        department.setUpdatedAt(LocalDateTime.now());
        Department updatedDepartment = departmentRepository.save(department);
        
        log.info("Updated department: {} (ID: {})", updatedDepartment.getName(), departmentId);
        return mapToDepartmentResponse(updatedDepartment);
    }
    
    /**
     * Delete department (soft delete by setting isActive = false)
     */
    @Transactional
    public void deleteDepartment(String tenantAdminEmail, Integer departmentId) {
        User tenantAdmin = getUserByEmail(tenantAdminEmail);
        UUID tenantId = tenantAdmin.getTenantId();
        
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Phòng ban không tồn tại với ID: " + departmentId));
        
        // Verify same tenant
        if (!department.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Bạn không có quyền xóa phòng ban này!");
        }
        
        // Check if there are users in this department
        long userCount = userRepository.findByTenantId(tenantId).stream()
                .filter(u -> departmentId.equals(u.getDepartmentId()))
                .count();
        
        if (userCount > 0) {
            throw new RuntimeException("Không thể xóa phòng ban có nhân viên! Vui lòng chuyển " + userCount + " nhân viên sang phòng ban khác trước.");
        }
        
        log.info("Deleting department: {} (ID: {})", department.getName(), departmentId);
        
        // Soft delete: just set isActive = false
        department.setIsActive(false);
        department.setUpdatedAt(LocalDateTime.now());
        departmentRepository.save(department);
        
        log.info("Department soft deleted: {} (ID: {})", department.getName(), departmentId);
    }
    
    /**
     * Map Department entity to DepartmentResponse
     */
    private DepartmentResponse mapToDepartmentResponse(Department department) {
        // Count employees in this department
        int employeeCount = (int) userRepository.findByTenantId(department.getTenantId()).stream()
                .filter(u -> department.getId().equals(u.getDepartmentId()))
                .count();
        
        return DepartmentResponse.builder()
                .id(department.getId())
                .tenantId(department.getTenantId())
                .code(department.getCode())
                .name(department.getName())
                .description(department.getDescription())
                .isActive(department.getIsActive())
                .employeeCount(employeeCount)
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }
    
    /**
     * Get user by email
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User không tồn tại với email: " + email));
    }
}
