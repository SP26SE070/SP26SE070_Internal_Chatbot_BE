package com.gsp26se114.chatbot_rag_be.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRegistrationServiceTest {

    @Test
    void rejectsRepeatedFinalDomainLabel() {
        assertThatThrownBy(() -> TenantRegistrationService.validateEmailDomain("user@fpt.edu.vn.vn"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email người đại diện không hợp lệ");
    }

    @Test
    void rejectsRepeatedComDomain() {
        assertThatThrownBy(() -> TenantRegistrationService.validateEmailDomain("user@example.com.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email người đại diện không hợp lệ");
    }

    @Test
    void rejectsRepeatedEduDomain() {
        assertThatThrownBy(() -> TenantRegistrationService.validateEmailDomain("user@company.edu.edu"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email người đại diện không hợp lệ");
    }

    @Test
    void acceptsNormalEduDomain() {
        assertThatCode(() -> TenantRegistrationService.validateEmailDomain("user@fpt.edu.vn"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNormalComDomain() {
        assertThatCode(() -> TenantRegistrationService.validateEmailDomain("user@example.com.vn"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsSimpleDomain() {
        assertThatCode(() -> TenantRegistrationService.validateEmailDomain("user@example.com"))
                .doesNotThrowAnyException();
    }
}
