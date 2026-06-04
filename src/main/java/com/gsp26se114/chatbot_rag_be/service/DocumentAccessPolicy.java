package com.gsp26se114.chatbot_rag_be.service;

import com.gsp26se114.chatbot_rag_be.entity.DocumentEntity;
import com.gsp26se114.chatbot_rag_be.entity.DocumentVisibility;
import com.gsp26se114.chatbot_rag_be.security.service.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Quy tắc xem tài liệu theo cấp bậc (1 = cao nhất) và phòng ban:
 * <ul>
 *   <li>Level 1–2 (+ TENANT_ADMIN): xem mọi phòng ban trong tenant (theo ngưỡng level).</li>
 *   <li>Level 3–5: chỉ phòng ban của mình; level thấp hơn (số nhỏ hơn) xem được tài liệu
 *       gắn {@code minimum_role_level} cao hơn (4, 5) trong cùng phòng ban.</li>
 * </ul>
 */
@Component
public class DocumentAccessPolicy {

    /** Level 1–2 được xem toàn bộ phòng ban. */
    public static final int ORG_WIDE_MAX_ROLE_LEVEL = 2;

    public boolean isOrgWideViewer(UserPrincipal user) {
        if (user == null) {
            return false;
        }
        if ("TENANT_ADMIN".equalsIgnoreCase(user.getRoleCode())) {
            return true;
        }
        Integer level = user.getRoleLevel();
        return level != null && level <= ORG_WIDE_MAX_ROLE_LEVEL;
    }

    /**
     * User level X thấy tài liệu khi {@code minimumRoleLevel >= X}
     * (tài liệu upload bởi level 4/5 trong phòng ban → level 3 vẫn thấy).
     */
    public boolean passesRoleLevelGate(Integer userRoleLevel, Integer documentMinimumRoleLevel) {
        if (userRoleLevel == null || documentMinimumRoleLevel == null) {
            return false;
        }
        return documentMinimumRoleLevel >= userRoleLevel;
    }

    public boolean passesDepartmentGate(UserPrincipal user, DocumentEntity doc) {
        if (isOrgWideViewer(user)) {
            return true;
        }
        if (doc.getUploadedBy() != null && doc.getUploadedBy().equals(user.getId())) {
            return true;
        }
        Integer userDept = user.getDepartmentId();
        if (userDept == null) {
            return false;
        }
        if (doc.getOwnerDepartmentId() != null) {
            return userDept.equals(doc.getOwnerDepartmentId());
        }
        if (doc.getVisibility() == DocumentVisibility.COMPANY_WIDE) {
            return false;
        }
        return matchesLegacyDepartmentVisibility(doc, userDept, user.getRoleId());
    }

    public boolean canRead(UserPrincipal user, DocumentEntity doc) {
        if (user == null || doc == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(doc.getIsActive())) {
            return false;
        }
        if (!Objects.equals(user.getTenantId(), doc.getTenantId())) {
            return false;
        }
        if (doc.getUploadedBy() != null && doc.getUploadedBy().equals(user.getId())) {
            return true;
        }
        if (!passesRoleLevelGate(user.getRoleLevel(), doc.getMinimumRoleLevel())) {
            return false;
        }
        if (!passesDepartmentGate(user, doc)) {
            return false;
        }
        if (!isOrgWideViewer(user) && !passesVisibilityScope(user, doc)) {
            return false;
        }
        return true;
    }

    /**
     * Metadata mặc định khi upload — không ghi đè {@code visibility} / danh sách phòng ban-vai trò
     * mà user đã chọn (SPECIFIC_DEPARTMENTS, SPECIFIC_ROLES, …).
     */
    public void applyUploadDefaults(UserPrincipal user, DocumentEntity document) {
        document.setOwnerDepartmentId(user.getDepartmentId());
        document.setMinimumRoleLevel(clampLevel(user.getRoleLevel()));
    }

    /**
     * Gợi ý visibility khi form để COMPANY_WIDE và user level 3–5 (chỉ áp dụng nếu FE không đổi scope).
     */
    public void applyUploadVisibilityFallback(UserPrincipal user, DocumentEntity document) {
        if (document.getVisibility() != null
                && document.getVisibility() != DocumentVisibility.COMPANY_WIDE) {
            return;
        }
        int uploaderLevel = clampLevel(user.getRoleLevel());
        if (uploaderLevel <= ORG_WIDE_MAX_ROLE_LEVEL) {
            document.setVisibility(DocumentVisibility.COMPANY_WIDE);
            document.setAccessibleDepartments(null);
            document.setAccessibleRoles(null);
        } else {
            document.setVisibility(DocumentVisibility.SPECIFIC_DEPARTMENTS);
            Integer deptId = user.getDepartmentId();
            document.setAccessibleDepartments(deptId != null ? List.of(deptId) : null);
            document.setAccessibleRoles(null);
        }
    }

    /** Kiểm tra phạm vi SPECIFIC_* (sau khi đã qua cổng level + phòng ban). */
    public boolean passesVisibilityScope(UserPrincipal user, DocumentEntity doc) {
        if (doc.getVisibility() == null || doc.getVisibility() == DocumentVisibility.COMPANY_WIDE) {
            return true;
        }
        Integer userDept = user.getDepartmentId();
        Integer userRoleId = user.getRoleId();
        return switch (doc.getVisibility()) {
            case SPECIFIC_DEPARTMENTS -> doc.getAccessibleDepartments() != null
                    && userDept != null
                    && doc.getAccessibleDepartments().contains(userDept);
            case SPECIFIC_ROLES -> doc.getAccessibleRoles() != null
                    && userRoleId != null
                    && doc.getAccessibleRoles().contains(userRoleId);
            case SPECIFIC_DEPARTMENTS_AND_ROLES -> doc.getAccessibleDepartments() != null
                    && doc.getAccessibleRoles() != null
                    && userDept != null
                    && userRoleId != null
                    && doc.getAccessibleDepartments().contains(userDept)
                    && doc.getAccessibleRoles().contains(userRoleId);
            default -> true;
        };
    }

    private boolean matchesLegacyDepartmentVisibility(DocumentEntity doc, Integer userDept, Integer userRoleId) {
        if (doc.getVisibility() == DocumentVisibility.COMPANY_WIDE) {
            return true;
        }
        if (doc.getVisibility() == DocumentVisibility.SPECIFIC_DEPARTMENTS) {
            return doc.getAccessibleDepartments() != null && doc.getAccessibleDepartments().contains(userDept);
        }
        if (doc.getVisibility() == DocumentVisibility.SPECIFIC_ROLES) {
            return doc.getAccessibleRoles() != null
                    && userRoleId != null
                    && doc.getAccessibleRoles().contains(userRoleId);
        }
        if (doc.getVisibility() == DocumentVisibility.SPECIFIC_DEPARTMENTS_AND_ROLES) {
            return doc.getAccessibleDepartments() != null
                    && doc.getAccessibleRoles() != null
                    && doc.getAccessibleDepartments().contains(userDept)
                    && userRoleId != null
                    && doc.getAccessibleRoles().contains(userRoleId);
        }
        return false;
    }

    private static int clampLevel(Integer level) {
        int v = level != null ? level : 4;
        return Math.max(1, Math.min(5, v));
    }
}
