package com.gsp26se114.chatbot_rag_be.security.service;

import com.gsp26se114.chatbot_rag_be.entity.RoleEntity;
import com.gsp26se114.chatbot_rag_be.entity.RoleType;
import com.gsp26se114.chatbot_rag_be.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UserPrincipalTest {

    @Test
    void buildKeepsPlatformRoleLevelsPrivileged() {
        UserPrincipal superAdmin = UserPrincipal.build(user(1), role("SUPER_ADMIN", 1, RoleType.SYSTEM));
        UserPrincipal staff = UserPrincipal.build(user(2), role("STAFF", 2, RoleType.SYSTEM));

        assertThat(superAdmin.getRoleLevel()).isEqualTo(1);
        assertThat(staff.getRoleLevel()).isEqualTo(2);
    }

    @Test
    void buildDefaultsCustomNullLevelRoleToLowPrivilege() {
        RoleEntity customNullLevelRole = role("CUSTOM_SUPPORT", null, RoleType.CUSTOM);

        assertThatCode(() -> UserPrincipal.build(user(3), customNullLevelRole))
                .doesNotThrowAnyException();

        UserPrincipal principal = UserPrincipal.build(user(3), customNullLevelRole);
        assertThat(principal.getRoleLevel()).isEqualTo(4);
        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("ROLE_SUPER_ADMIN", "ROLE_STAFF", "ALL");
    }

    private static User user(int roleId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user" + roleId + "@example.com");
        user.setPassword("secret");
        user.setRoleId(roleId);
        user.setTokenVersion(1);
        return user;
    }

    private static RoleEntity role(String code, Integer level, RoleType roleType) {
        RoleEntity role = new RoleEntity();
        role.setId(1);
        role.setCode(code);
        role.setName(code);
        role.setLevel(level);
        role.setRoleType(roleType);
        return role;
    }
}
