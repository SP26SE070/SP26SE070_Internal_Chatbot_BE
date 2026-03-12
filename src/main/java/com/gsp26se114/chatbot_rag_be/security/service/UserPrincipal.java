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
import java.util.List;
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

    public static UserPrincipal build(User user, RoleEntity role) {
        String roleCode = role != null ? role.getCode() : "EMPLOYEE";

        // Build full authority list: ROLE_ prefix + permission-based authorities
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));

        // Add basic role permissions (from RolePermissionConstants)
        List<String> basicPerms = RolePermissionConstants.getBasicPermissions(roleCode);
        for (String perm : basicPerms) {
            authorities.add(new SimpleGrantedAuthority(perm));
            // Expand X_ALL → X_READ, X_WRITE, X_DELETE
            if (perm.endsWith("_ALL")) {
                String prefix = perm.substring(0, perm.length() - 4);
                authorities.add(new SimpleGrantedAuthority(prefix + "_READ"));
                authorities.add(new SimpleGrantedAuthority(prefix + "_WRITE"));
                authorities.add(new SimpleGrantedAuthority(prefix + "_DELETE"));
            }
        }

        // Add user-specific extra permissions granted by TENANT_ADMIN
        if (user.getPermissions() != null) {
            for (String perm : user.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(perm));
                if (perm.endsWith("_ALL")) {
                    String prefix = perm.substring(0, perm.length() - 4);
                    authorities.add(new SimpleGrantedAuthority(prefix + "_READ"));
                    authorities.add(new SimpleGrantedAuthority(prefix + "_WRITE"));
                    authorities.add(new SimpleGrantedAuthority(prefix + "_DELETE"));
                }
            }
        }

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