package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for tenant organization registration
 * Used by companies to request platform access
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TenantRegistrationRequest {
    
    // Company Information
    @NotBlank(message = "Tên công ty không được để trống")
    @Size(max = 255, message = "Tên công ty không được quá 255 ký tự")
    private String companyName;
    
    @Size(max = 500, message = "Địa chỉ không được quá 500 ký tự")
    private String address;
    
    @Size(max = 255, message = "Website không được quá 255 ký tự")
    private String website;
    
    @Size(max = 100, message = "Lĩnh vực không được quá 100 ký tự")
    private String industry; // IT, Finance, Healthcare, Education, etc.
    
    @Size(max = 50, message = "Quy mô không hợp lệ")
    private String companySize; // "1-50", "51-200", "201-500", "500+"
    
    // Representative Information
    @NotBlank(message = "Email người đại diện không được để trống")
    @Email(message = "Email không hợp lệ")
    private String contactEmail;
    
    @NotBlank(message = "Tên người đại diện không được để trống")
    @Size(max = 100, message = "Tên người đại diện không được quá 100 ký tự")
    private String representativeName;
    
    @Size(max = 100, message = "Chức vụ không được quá 100 ký tự")
    private String representativePosition; // CEO, CTO, HR Manager, etc.
    
    @Pattern(regexp = "^[0-9+\\-\\s()]{10,20}$", message = "Số điện thoại không hợp lệ")
    private String representativePhone;
    
    // Request Message
    @Size(max = 1000, message = "Lý do đăng ký không được quá 1000 ký tự")
    private String requestMessage; // Why want to use the platform
}
