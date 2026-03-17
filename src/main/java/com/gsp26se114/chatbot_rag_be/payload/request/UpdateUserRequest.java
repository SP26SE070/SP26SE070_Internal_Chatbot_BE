package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserRequest(
        @Size(max = 255, message = "Họ tên không được quá 255 ký tự")
        String fullName,

        @Pattern(
            regexp = "^(0\\d{9}|\\+84\\d{9})$",
            message = "Số điện thoại không hợp lệ. Chỉ chấp nhận định dạng 0xxxxxxxxx hoặc +84xxxxxxxxx (9 chữ số sau +84)"
        )
        String phoneNumber,

        LocalDate dateOfBirth,

        @Size(max = 500, message = "Địa chỉ không được quá 500 ký tự")
        String address,

        @Min(value = 1, message = "departmentId phải là số nguyên dương")
        Integer departmentId,

        @Min(value = 1, message = "roleId phải là số nguyên dương")
        Integer roleId
) {
    // Compact constructor for custom validation
    public UpdateUserRequest {
        if (dateOfBirth != null) {
            LocalDate today = LocalDate.now();
            LocalDate minDate = today.minusYears(10);   // must be at least 10 years old
            LocalDate maxDate = today.minusYears(100);  // must be at most 100 years old

            if (dateOfBirth.isAfter(minDate)) {
                throw new IllegalArgumentException(
                    "Ngày sinh không hợp lệ. Người dùng phải ít nhất 10 tuổi " +
                    "(ngày sinh phải trước ngày " + minDate + ")"
                );
            }
            if (dateOfBirth.isBefore(maxDate)) {
                throw new IllegalArgumentException(
                    "Ngày sinh không hợp lệ. Người dùng không thể quá 100 tuổi " +
                    "(ngày sinh phải sau ngày " + maxDate + ")"
                );
            }
        }
    }
}
