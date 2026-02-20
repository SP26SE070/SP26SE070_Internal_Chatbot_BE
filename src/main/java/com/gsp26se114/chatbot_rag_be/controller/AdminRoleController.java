package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.CreateRoleRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateRoleRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.RoleResponse;
import com.gsp26se114.chatbot_rag_be.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Tag(name = "06. 📊 Super Admin - Role Management", 
     description = """
         ## 🎭 QUẢN LÝ ROLES HỆ THỐNG
         
         **Chỉ dành cho SUPER_ADMIN**
         
         ### 📚 Giải thích về Role System:
         
         #### 1️⃣ **System Roles** (Roles mặc định của hệ thống):
         - `SUPER_ADMIN`: Quản trị viên tối cao - Quản lý toàn bộ hệ thống
         - `TENANT_ADMIN`: Quản trị viên tenant - Quản lý users trong tenant
         - `CONTENT_MANAGER`: Quản lý nội dung - Quản lý tài liệu, knowledge base
         - `EMPLOYEE`: Nhân viên - Quyền cơ bản
         - **Đặc điểm**: `tenantId = null`, không thể xóa, code không thể sửa
         
         #### 2️⃣ **Tenant-Specific Roles** (Roles tùy chỉnh của từng tenant):
         - Ví dụ: `TEAM_LEADER`, `SENIOR_DEVELOPER`, `HR_MANAGER`...
         - **Đặc điểm**: Gắn với `tenantId` cụ thể, có thể tạo/sửa/xóa
         - **Mục đích**: Tạo phân quyền chi tiết hơn cho từng tổ chức
         
         ### ⚙️ Chức năng:
         - ✅ Xem tất cả roles (system + custom)
         - ✅ Tạo role mới (system hoặc tenant-specific)
         - ✅ Cập nhật thông tin role (name, description)
         - ✅ Xóa role (có validation)
         - ✅ Lọc roles theo tenant hoặc system
         """)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRoleController {
    
    private final RoleService roleService;
    
    // =====================================================================
    // 📋 QUERY APIs - Xem danh sách và chi tiết roles
    // =====================================================================
    
    /**
     * 📋 Lấy TẤT CẢ roles trong hệ thống
     * 
     * @return Danh sách tất cả roles (bao gồm system roles và tenant-specific roles)
     * 
     * Use case:
     * - Xem tổng quan tất cả roles trong hệ thống
     * - Quản lý và kiểm tra roles đã tạo
     * 
     * Response bao gồm:
     * - id: Role ID (Integer)
     * - code: Mã role (UPPERCASE, ví dụ: EMPLOYEE, TEAM_LEADER)
     * - name: Tên hiển thị (ví dụ: "Employee", "Team Leader")
     * - description: Mô tả vai trò
     * - isSystemRole: true nếu là system role, false nếu là custom role
     * - tenantId: null nếu là system role, UUID tenant nếu là custom role
     * - tenantName: Tên tenant (nếu là custom role)
     */
    @GetMapping
    @Operation(summary = "📋 Lấy tất cả roles", 
               description = """
                   Lấy danh sách đầy đủ tất cả roles trong hệ thống.
                   
                   **Bao gồm:**
                   - System roles (SUPER_ADMIN, TENANT_ADMIN, CONTENT_MANAGER, EMPLOYEE)
                   - Tenant-specific roles (Custom roles của từng tenant)
                   
                   **Sắp xếp:** System roles trước, sau đó đến custom roles theo tenant
                   """)
    public ResponseEntity<List<RoleResponse>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }
    
    /**
     *  Xem chi tiết một role
     * 
     * @param roleId ID của role (Integer)
     * @return Thông tin chi tiết role
     * 
     * Use case:
     * - Xem đầy đủ thông tin của role
     * - Kiểm tra role thuộc tenant nào
     * - Chuẩn bị dữ liệu cho form edit
     */
    @GetMapping("/{roleId}")
    @Operation(summary = "🔍 Xem chi tiết role", 
               description = """
                   Lấy thông tin chi tiết của một role theo ID.
                   
                   **Response bao gồm:**
                   - id: Role ID
                   - code: Mã role (UPPERCASE)
                   - name: Tên hiển thị
                   - description: Mô tả
                   - isSystemRole: Có phải system role không
                   - tenantId: UUID tenant (null nếu là system role)
                   - tenantName: Tên tenant
                   - createdAt: Thời gian tạo
                   """)
    public ResponseEntity<RoleResponse> getRoleById(@PathVariable Integer roleId) {
        RoleResponse role = roleService.getRoleById(roleId);
        return ResponseEntity.ok(role);
    }
    
    // =====================================================================
    // ➕ CREATE API - Tạo role mới
    // =====================================================================
    
    /**
     * ➕ Tạo role mới
     * 
     * @param request CreateRoleRequest chứa thông tin role mới
     * @return Role vừa được tạo
     * 
     * Use case:
     * - Tạo system role mới (tenantId = null)
     * - Tạo custom role cho tenant (tenantId = UUID)
     * 
     * Validation:
     * - code: PHẢI UPPERCASE, chỉ chứa chữ cái và underscore (^[A-Z_]+$)
     * - code: PHẢI UNIQUE trong hệ thống
     * - name: Bắt buộc, 1-100 ký tự
     * - description: Tùy chọn, max 500 ký tự
     * - tenantId: null = system role, UUID = tenant role
     * 
     * Example Request:
     * {
     *   "code": "TEAM_LEADER",
     *   "name": "Team Leader",
     *   "description": "Leads a team of employees",
     *   "tenantId": "660e8400-e29b-41d4-a716-446655440000"
     * }
     */
    @PostMapping
    @Operation(summary = "➕ Tạo role mới", 
               description = """
                   Tạo role mới trong hệ thống (system role hoặc tenant-specific role).
                   
                   **Request Body:**
                   ```json
                   {
                     "code": "TEAM_LEADER",
                     "name": "Team Leader",
                     "description": "Leads a team",
                     "tenantId": "uuid-or-null"
                   }
                   ```
                   
                   **Validation Rules:**
                   - `code`: UPPERCASE only, pattern ^[A-Z_]+$, UNIQUE
                   - `name`: Bắt buộc, 1-100 chars
                   - `description`: Tùy chọn, max 500 chars
                   - `tenantId`: null = system role, UUID = tenant role
                   
                   **Lưu ý:**
                   - Code không thể sửa sau khi tạo
                   - System roles chỉ SUPER_ADMIN mới tạo được
                   - Code phải UNIQUE toàn hệ thống
                   """)
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse role = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }
    
    // =====================================================================
    // ✏️ UPDATE API - Cập nhật role
    // =====================================================================
    
    /**
     * ✏️ Cập nhật thông tin role
     * 
     * @param roleId ID của role cần cập nhật
     * @param request UpdateRoleRequest chứa thông tin cập nhật
     * @return Role sau khi cập nhật
     * 
     * Use case:
     * - Sửa tên hiển thị role
     * - Cập nhật mô tả role
     * 
     * Lưu ý:
     * - KHÔNG thể sửa code (code là immutable)
     * - KHÔNG thể sửa tenantId
     * - CHỈ sửa được name và description
     * 
     * Validation:
     * - name: 1-100 ký tự
     * - description: Max 500 ký tự
     * 
     * Example Request:
     * {
     *   "name": "Senior Team Leader",
     *   "description": "Leads multiple teams and projects"
     * }
     */
    @PutMapping("/{roleId}")
    @Operation(summary = "✏️ Cập nhật role", 
               description = """
                   Cập nhật thông tin role (CHỈ name và description).
                   
                   **Request Body:**
                   ```json
                   {
                     "name": "Senior Team Leader",
                     "description": "Updated description"
                   }
                   ```
                   
                   **Có thể sửa:**
                   - ✅ name (Tên hiển thị)
                   - ✅ description (Mô tả)
                   
                   **KHÔNG thể sửa:**
                   - ❌ code (Immutable)
                   - ❌ tenantId (Immutable)
                   - ❌ isSystemRole (Immutable)
                   
                   **Validation:**
                   - name: 1-100 chars
                   - description: max 500 chars
                   """)
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable Integer roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse role = roleService.updateRole(roleId, request);
        return ResponseEntity.ok(role);
    }
    
    // =====================================================================
    // 🗑️ DELETE API - Xóa role
    // =====================================================================
    
    /**
     * 🗑️ Xóa role
     * 
     * @param roleId ID của role cần xóa
     * @return Thông báo xóa thành công
     * 
     * Use case:
     * - Xóa custom role không còn sử dụng
     * - Dọn dẹp roles thừa
     * 
     * Validation (SẼ BỊ TỪ CHỐI nếu):
     * - ❌ Đang xóa system role (SUPER_ADMIN, TENANT_ADMIN, CONTENT_MANAGER, EMPLOYEE)
     * - ❌ Role đang được gán cho users (phải remove users khỏi role trước)
     * 
     * Quy trình an toàn:
     * 1. Kiểm tra role có đang được sử dụng không
     * 2. Nếu có users: Chuyển users sang role khác trước
     * 3. Sau đó mới xóa role
     * 
     * Lưu ý:
     * - System roles KHÔNG THỂ xóa
     * - Phải check users trước khi xóa
     */
    @DeleteMapping("/{roleId}")
    @Operation(summary = "🗑️ Xóa role", 
               description = """
                   Xóa role khỏi hệ thống (CÓ VALIDATION).
                   
                   **⚠️ Không thể xóa nếu:**
                   - ❌ Là system role (SUPER_ADMIN, TENANT_ADMIN, CONTENT_MANAGER, EMPLOYEE)
                   - ❌ Role đang được gán cho users
                   
                   **✅ Có thể xóa:**
                   - Custom roles không còn users nào sử dụng
                   
                   **Quy trình an toàn:**
                   1. Kiểm tra role có users không
                   2. Chuyển users sang role khác
                   3. Xóa role
                   
                   **Response khi thành công:**
                   ```json
                   {
                     "message": "Role đã được xóa thành công!"
                   }
                   ```
                   
                   **Errors:**
                   - 400: Không thể xóa system role
                   - 409: Role đang được sử dụng bởi users
                   - 404: Role không tồn tại
                   """)
    public ResponseEntity<MessageResponse> deleteRole(@PathVariable Integer roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.ok(new MessageResponse("Role đã được xóa thành công!"));
    }
}
