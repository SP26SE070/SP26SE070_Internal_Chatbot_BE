package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO cho yêu cầu chuyển phòng ban
 */
@Data
public class DepartmentTransferRequestDTO {
    
    @NotNull(message = "Department ID mới không được để trống")
    private Integer requestedDepartmentId;
    
    @NotBlank(message = "Lý do chuyển phòng không được để trống")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10-500 ký tự")
    private String reason;
}
