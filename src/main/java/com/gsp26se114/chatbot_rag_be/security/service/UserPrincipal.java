package com.gsp26se114.chatbot_rag_be.security.service;

import com.gsp26se114.chatbot_rag_be.constants.RolePermissionConstants;
import com.gsp26se114.chatbot_rag_be.entity.User;
import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private UUID id;
    private String email;
    @JsonIgnore
    private String password;
    private UUID tenantId;
    private Integer departmentId; 
    private Integer roleId;
    private String roleCode;
    private Collection<? extends GrantedAuthority> authorities;

    private static void addPermissionWithAllExpansion(List<GrantedAuthority> authorities, String perm) {
        if (perm == null || perm.isBlank()) {
            return;
        }
        String p = perm.trim();
        authorities.add(new SimpleGrantedAuthority(p));
        if (p.endsWith("_ALL")) {
            String prefix = p.substring(0, p.length() - 4);
            authorities.add(new SimpleGrantedAuthority(prefix + "_READ"));
            authorities.add(new SimpleGrantedAuthority(prefix + "_WRITE"));
            authorities.add(new SimpleGrantedAuthority(prefix + "_DELETE"));
        }
    }

    private static void addPermissionList(List<GrantedAuthority> authorities, List<String> perms) {
        if (perms == null) {
            return;
        }
        for (String perm : perms) {
            addPermissionWithAllExpansion(authorities, perm);
        }
    }

    public static UserPrincipal build(User user, RoleEntity role) {
        String roleCode = role != null ? role.getCode() : "EMPLOYEE";

        // Build full authority list: ROLE_ prefix + permission-based authorities
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));

        // Merge old + new behavior: default role perms + DB role perms, de-duplicated.
        Set<String> effectiveRolePerms = new LinkedHashSet<>(RolePermissionConstants.getBasicPermissions(roleCode));
        if (role != null && role.getPermissions() != null) {
            effectiveRolePerms.addAll(role.getPermissions());
        }
        addPermissionList(authorities, new ArrayList<>(effectiveRolePerms));

        // User-specific extra permissions
        addPermissionList(authorities, user.getPermissions());

        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getTenantId(),
                user.getDepartmentId(),
                user.getRoleId(),
                roleCode,
                authorities
        );
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}