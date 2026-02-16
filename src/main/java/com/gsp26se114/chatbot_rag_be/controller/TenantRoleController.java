package com.gsp26se114.chatbot_rag_be.controller;

import com.gsp26se114.chatbot_rag_be.payload.request.CreateRoleRequest;
import com.gsp26se114.chatbot_rag_be.payload.request.UpdateRoleRequest;
import com.gsp26se114.chatbot_rag_be.payload.response.MessageResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.PermissionCategoryResponse;
import com.gsp26se114.chatbot_rag_be.payload.response.RoleResponse;
import com.gsp26se114.chatbot_rag_be.service.PermissionService;
import com.gsp26se114.chatbot_rag_be.service.TenantRoleService;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenant-admin/roles")
@RequiredArgsConstructor
@Tag(name = "13. 🎭 Tenant Role Management", 
     description = """
         ## 🎭 QUẢN LÝ ROLES TRONG TENANT
         
         **Chỉ dành cho TENANT_ADMIN**
         
         ### 📚 Giải thích:
         
         #### 🔒 **Fixed Roles** (Roles cố định - Không thể sửa/xóa):
         - `TENANT_ADMIN`: Quản trị viên tenant - Full quyền trong tenant
         - `CONTENT_MANAGER`: Quản lý nội dung - Quản lý documents
         - `EMPLOYEE`: Nhân viên - Quyền cơ bản
         
         #### ✨ **Custom Roles** (Roles tùy chỉnh - Tự do tạo/sửa/xóa):
         - TENANT_ADMIN có thể tạo custom roles theo nhu cầu công ty
         - Ví dụ: ACCOUNTANT, HR_MANAGER, SALES_LEAD, DEV_LEAD...
         - Gán permissions chi tiết cho từng role
         
         ### ⚙️ Chức năng:
         - ✅ Xem tất cả roles có sẵn (fixed + custom)
         - ✅ Tạo custom role mới
         - ✅ Cập nhật custom role (name, description, permissions)
         - ✅ Xóa custom role (check users assigned)
         - ✅ Xem danh sách permissions có thể gán
         
         ### 🔐 Permissions System:
         - USER_ALL, USER_READ, USER_WRITE, USER_DELETE
         - DEPT_ALL, DEPT_READ, DEPT_WRITE, DEPT_DELETE
         - ROLE_ALL, ROLE_READ, ROLE_WRITE
         - DOCUMENT_ALL, DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_DELETE
         - SUBSCRIPTION_MANAGE, ANALYTICS_VIEW, PROFILE_MANAGE
         """)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantRoleController {
    
    private final TenantRoleService tenantRoleService;
    private final PermissionService permissionService;
    
    // =====================================================================
    // 📋 QUERY APIs - Xem roles
    // =====================================================================
    
    /**
     * 📋 Lấy tất cả roles có sẵn cho tenant (FIXED + CUSTOM)
     */
    @GetMapping
    @Operation(summary = "📋 Xem tất cả roles", 
               description = """
                   Lấy danh sách tất cả roles có sẵn cho tenant.
                   
                   **Bao gồm:**
                   - Fixed roles: TENANT_ADMIN, CONTENT_MANAGER, EMPLOYEE (3 roles)
                   - Custom roles: Roles do TENANT_ADMIN tạo
                   
                   **Response:**
                   - Mỗi role có: id, code, name, permissions[], usersCount
                   - isFixed = true → không thể sửa/xóa
                   - isCustom = true → có thể sửa/xóa
                   """)
    public ResponseEntity<List<RoleResponse>> getAvailableRoles(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<RoleResponse> roles = tenantRoleService.getAvailableRolesForTenant(principal.getTenantId());
        return ResponseEntity.ok(roles);
    }
    
    /**
     * 📋 Lấy chỉ custom roles
     */
    @GetMapping("/custom")
    @Operation(summary = "📋 Xem custom roles", 
               description = """
                   Lấy danh sách custom roles do tenant tạo.
                   
                   **Use case:**
                   - Quản lý custom roles đã tạo
                   - Xem số lượng users được gán mỗi role
                   - Kiểm tra permissions của từng role
                   """)
    public ResponseEntity<List<RoleResponse>> getCustomRoles(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<RoleResponse> roles = tenantRoleService.getCustomRolesForTenant(principal.getTenantId());
        return ResponseEntity.ok(roles);
    }
    
    /**
     * 📋 Lấy chỉ fixed roles
     */
    @GetMapping("/fixed")
    @Operation(summary = "📋 Xem fixed roles", 
               description = """
                   Lấy danh sách fixed roles (TENANT_ADMIN, CONTENT_MANAGER, EMPLOYEE).
                   
                   **Đặc điểm:**
                   - Không thể sửa/xóa
                   - Có sẵn cho tất cả tenants
                   - Permissions được định nghĩa sẵn
                   """)
    public ResponseEntity<List<RoleResponse>> getFixedRoles() {
        List<RoleResponse> roles = tenantRoleService.getTenantFixedRoles();
        return ResponseEntity.ok(roles);
    }
    
    /**
     * 🔍 Xem chi tiết một role
     */
    @GetMapping("/{roleId}")
    @Operation(summary = "🔍 Chi tiết role", 
               description = """
                   Xem thông tin chi tiết của một role.
                   
                   **Response bao gồm:**
                   - id, code, name, description
                   - roleType: FIXED hoặc CUSTOM
                   - permissions[]: Danh sách permissions
                   - usersCount: Số users được gán role này (chỉ có với custom roles)
                   
                   **Security:**
                   - Custom roles phải thuộc về tenant của user
                   - Fixed roles thì ai cũng xem được
                   """)
    public ResponseEntity<RoleResponse> getRoleById(
            @PathVariable Integer roleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        RoleResponse role = tenantRoleService.getRoleById(roleId, principal.getTenantId());
        return ResponseEntity.ok(role);
    }
    
    // =====================================================================
    // ✏️ CREATE/UPDATE/DELETE APIs - Quản lý custom roles
    // =====================================================================
    
    /**
     * ➕ Tạo custom role mới
     */
    @PostMapping
    @Operation(summary = "➕ Tạo custom role", 
               description = """
                   Tạo custom role mới cho tenant.
                   
                   **Request Body:**
                   ```json
                   {
                     "code": "ACCOUNTANT",
                     "name": "Kế toán",
                     "description": "Quản lý tài chính công ty",
                     "permissions": [
                       "DOCUMENT_READ",
                       "ANALYTICS_VIEW",
                       "USER_READ"
                     ]
                   }
                   ```
                   
                   **Validation:**
                   - code: UPPERCASE, A-Z và _
                   - code phải unique trong tenant
                   - permissions: phải hợp lệ (xem /permissions/available)
                   - Không được gán ALL, TENANT_APPROVE (system permissions)
                   
                   **Ví dụ custom roles:**
                   - ACCOUNTANT: Kế toán
                   - HR_MANAGER: Quản lý nhân sự
                   - DEV_LEAD: Trưởng nhóm phát triển
                   - SALES_MANAGER: Quản lý kinh doanh
                   """)
    public ResponseEntity<RoleResponse> createCustomRole(
            @Valid @RequestBody CreateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        RoleResponse role = tenantRoleService.createCustomRole(
            principal.getTenantId(), 
            request, 
            principal.getId() // Use getId() instead of getUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }
    
    /**
     * ✏️ Cập nhật custom role
     */
    @PutMapping("/{roleId}")
    @Operation(summary = "✏️ Cập nhật custom role", 
               description = """
                   Cập nhật name, description, permissions của custom role.
                   
                   **Request Body:**
                   ```json
                   {
                     "name": "Kế toán trưởng",
                     "description": "Quản lý tài chính và ngân sách",
                     "permissions": [
                       "DOCUMENT_ALL",
                       "ANALYTICS_VIEW",
                       "USER_READ"
                     ]
                   }
                   ```
                   
                   **Lưu ý:**
                   - Chỉ sửa được custom roles (không sửa được fixed roles)
                   - Role phải thuộc về tenant của user
                   - Không thể đổi code (code là immutable)
                   - Cập nhật permissions sẽ áp dụng cho tất cả users có role này
                   """)
    public ResponseEntity<RoleResponse> updateCustomRole(
            @PathVariable Integer roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        RoleResponse role = tenantRoleService.updateCustomRole(roleId, principal.getTenantId(), request);
        return ResponseEntity.ok(role);
    }
    
    /**
     * 🗑️ Xóa custom role
     */
    @DeleteMapping("/{roleId}")
    @Operation(summary = "🗑️ Xóa custom role", 
               description = """
                   Xóa custom role (có validation).
                   
                   **Validation:**
                   - Chỉ xóa được custom roles (không xóa được fixed roles)
                   - Role phải thuộc về tenant của user
                   - Không có users nào được gán role này
                   
                   **Response nếu có users:**
                   ```json
                   {
                     "success": false,
                     "message": "Không thể xóa role - 5 users đang được gán role này"
                   }
                   ```
                   
                   **Giải pháp:**
                   1. Xem danh sách users có role này: GET /tenant-admin/users?roleId={roleId}
                   2. Gán role khác cho users đó
                   3. Sau đó mới xóa role
                   """)
    public ResponseEntity<MessageResponse> deleteCustomRole(
            @PathVariable Integer roleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        tenantRoleService.deleteCustomRole(roleId, principal.getTenantId());
        return ResponseEntity.ok(new MessageResponse("Custom role đã được xóa thành công"));
    }
    
    // =====================================================================
    // 🔐 PERMISSIONS APIs - Xem permissions có thể gán
    // =====================================================================
    
    /**
     * 📚 Lấy danh sách permissions có thể gán cho custom roles
     */
    @GetMapping("/permissions/available")
    @Operation(summary = "📚 Danh sách permissions", 
               description = """
                   Lấy tất cả permissions có thể gán cho custom roles.
                   
                   **Response format:**
                   ```json
                   [
                     {
                       "category": "User Management",
                       "permissions": [
                         {
                           "code": "USER_READ",
                           "name": "View users",
                           "description": "Xem danh sách người dùng"
                         },
                         {
                           "code": "USER_WRITE",
                           "name": "Create/Edit users",
                           "description": "Tạo và sửa người dùng"
                         },
                         {
                           "code": "USER_ALL",
                           "name": "All user permissions",
                           "description": "Tất cả quyền quản lý người dùng"
                         }
                       ]
                     },
                     {
                       "category": "Document Management",
                       "permissions": [...]
                     }
                   ]
                   ```
                   
                   **Wildcard Permissions:**
                   - XXX_ALL bao gồm tất cả XXX_*
                   - Ví dụ: USER_ALL = USER_READ + USER_WRITE + USER_DELETE
                   
                   **Use case:**
                   - Hiển thị checkbox khi tạo/sửa custom role
                   - Validate permissions khi submit form
                   """)
    public ResponseEntity<List<PermissionCategoryResponse>> getAvailablePermissions() {
        List<PermissionCategoryResponse> permissions = permissionService.getAvailablePermissions();
        return ResponseEntity.ok(permissions);
    }
}
