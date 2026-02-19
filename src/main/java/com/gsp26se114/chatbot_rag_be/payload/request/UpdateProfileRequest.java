package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for updating basic profile information (not email, not departmentId, not fullName)
 * fullName và departmentId chỉ TENANT_ADMIN mới đổi được
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileRequest {
    
    @Size(max = 20, message = "Số điện thoại không được quá 20 ký tự")
    private String phoneNumber;
    
    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dateOfBirth;
    
    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String address;
}
