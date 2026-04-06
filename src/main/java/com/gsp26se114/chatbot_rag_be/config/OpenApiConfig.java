package com.gsp26se114.chatbot_rag_be.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@OpenAPIDefinition(
    info = @Info(title = "SP26SE070 Chatbot RAG API", version = "1.0"),
    security = @SecurityRequirement(name = "bearerAuth"),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local Development Server"),
        @Server(url = "https://718d91ceb9d2.ngrok-free.app", description = "Ngrok Tunnel (for webhook testing)")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {
    
        /**
         * Customizer to force Swagger tag order by configured sequence first,
         * then by numeric prefix fallback (01, 02, 03...).
         */
    @Bean
    public OpenApiCustomizer sortTagsAlphabetically() {
        return openApi -> {
                        List<Tag> desiredOrder = List.of(
                    new Tag().name("01. 🔐 Authentication")
                            .description("Đăng nhập, đăng ký, quên mật khẩu (Public)"),
                    
                    new Tag().name("02. 👤 User Profile")
                            .description("Quản lý thông tin cá nhân (All Users)"),
                    
                    new Tag().name("03. 👥 Super Admin - Staff Management")
                            .description("Quản lý tài khoản STAFF (SUPER_ADMIN)"),
                    
                    new Tag().name("04. 🛡️ Super Admin - Subscription Plan Management")
                            .description("Quản lý các gói subscription (SUPER_ADMIN)"),
                    
                    new Tag().name("05. 🔐 Super Admin - Subscription Management")
                            .description("Quản lý subscriptions của tenants (SUPER_ADMIN)"),
                    
                    new Tag().name("06. 📊 Super Admin - Role Management")
                            .description("Quản lý roles hệ thống (SUPER_ADMIN)"),

                    new Tag().name("06. 🔐 Super Admin - Tenant Lookup")
                            .description("Lấy danh sách tenant cho bộ lọc"),
                    
                    new Tag().name("07. 📊 Super Admin - System Analytics")
                            .description("Thống kê toàn hệ thống: subscription, plan, document & RAG, token/LLM usage (SUPER_ADMIN)"),
                    
                    new Tag().name("08. 📋 Staff - Tenant Management")
                            .description("Quản lý và phê duyệt tenants (STAFF)"),
                    
                    new Tag().name("09. 📊 Staff - Tenant Analytics")
                            .description("Thống kê về tenants, subscriptions (STAFF)"),
                    
                    new Tag().name("10. 💳 Staff - Transaction Management")
                            .description("Quản lý giao dịch thanh toán của tenants (STAFF)"),

                    new Tag().name("11. 🧾 Staff - Subscription Management")
                            .description("Danh sách subscriptions cho Staff Manage Subscriptions"),

                    new Tag().name("18. 🧠 Staff - Onboarding Content")
                            .description("Quản lý onboarding content theo tenant (STAFF)"),
                    
                    new Tag().name("11. 💳 Tenant Admin - Subscription")
                            .description("Xem và quản lý subscription (TENANT_ADMIN)"),
                    
                    new Tag().name("12. 👥 Tenant Admin - User Management")
                            .description("Quản lý users trong tenant (TENANT_ADMIN)"),
                    
                    new Tag().name("13. 🏢 Tenant Admin - Department Management")
                            .description("Quản lý phòng ban trong tenant (TENANT_ADMIN)"),
                    
                    new Tag().name("14. 🎭 Tenant Admin - Role Management")
                            .description("Quản lý roles trong tenant (TENANT_ADMIN)"),
                    
                    new Tag().name("15. 📊 Tenant Admin - Dashboard & Analytics")
                            .description("Dashboard và thống kê cho TENANT_ADMIN, bao gồm LLM usage tracking"),
                    
                    new Tag().name("16. 💳 Tenant Admin - Subscription Plans")
                            .description("Xem các gói subscription khả dụng (TENANT_ADMIN)"),
                    
                    new Tag().name("17. 💳 Payment Management")
                            .description("Quản lý thanh toán và giao dịch"),

                    new Tag().name("17. 📖 Employee Onboarding")
                            .description("Theo dõi tiến độ onboarding cho user trong tenant"),
                    
                    new Tag().name("18. 📚 Document Dashboard")
                            .description("Document upload and management APIs"),
                    
                    new Tag().name("19. 🗂️ Document Categories")
                            .description("Quản lý phân loại tài liệu theo cấu trúc cây"),
                    
                    new Tag().name("20. 🏷️ Document Tags")
                            .description("Quản lý bộ tag chuẩn cho tài liệu"),

                    new Tag().name("21. 🤖 Chatbot")
                            .description("RAG-powered chatbot APIs"),
                    
                    new Tag().name("99. 🔧 Admin - Document Management")
                            .description("Quản lý tài liệu nâng cao (chỉ dành cho SUPER_ADMIN)")
            );

                        Map<String, Integer> orderIndex = new LinkedHashMap<>();
                        for (int i = 0; i < desiredOrder.size(); i++) {
                                orderIndex.put(desiredOrder.get(i).getName(), i);
                        }

                        Map<String, Tag> mergedTags = new LinkedHashMap<>();
                        if (openApi.getTags() != null) {
                                for (Tag tag : openApi.getTags()) {
                                        if (tag != null && tag.getName() != null) {
                                                mergedTags.put(tag.getName(), tag);
                                        }
                                }
                        }

                        // Prefer canonical names/descriptions from desiredOrder.
                        for (Tag tag : desiredOrder) {
                                mergedTags.put(tag.getName(), tag);
                        }

                        List<Tag> sortedTags = new ArrayList<>(mergedTags.values());
                        sortedTags.sort(
                                        Comparator
                                                        .comparingInt((Tag tag) -> orderIndex.getOrDefault(tag.getName(), Integer.MAX_VALUE))
                                                        .thenComparingInt(tag -> extractOrderNumber(tag.getName()))
                                                        .thenComparing(Tag::getName, String.CASE_INSENSITIVE_ORDER)
                        );

                        openApi.setTags(sortedTags);
        };
    }

        private static int extractOrderNumber(String tagName) {
                if (tagName == null || tagName.isBlank()) {
                        return Integer.MAX_VALUE;
                }

                int dotIndex = tagName.indexOf('.');
                if (dotIndex <= 0) {
                        return Integer.MAX_VALUE;
                }

                String prefix = tagName.substring(0, dotIndex).trim();
                try {
                        return Integer.parseInt(prefix);
                } catch (NumberFormatException ignored) {
                        return Integer.MAX_VALUE;
                }
        }
}