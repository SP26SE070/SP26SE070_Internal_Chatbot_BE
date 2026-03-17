package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        String fullName,

        @NotBlank(message = "Contact email không được để trống")
        @Email(message = "Contact email không hợp lệ")
        String contactEmail,

        @Pattern(
            regexp = "^(0\\d{9}|\\+84\\d{9})$",
            message = "Số điện thoại không hợp lệ. Chỉ chấp nhận định dạng 0xxxxxxxxx hoặc +84xxxxxxxxx (9 chữ số sau +84)"
        )
        String phoneNumber,

        LocalDate dateOfBirth,

        @Size(max = 500, message = "Địa chỉ không được quá 500 ký tự")
        String address,

        @NotNull(message = "Role không được để trống")
        @Min(value = 1, message = "roleId phải là số nguyên dương")
        Integer roleId,

        @Min(value = 1, message = "departmentId phải là số nguyên dương")
        Integer departmentId,

        List<String> permissions
) {
    // Compact constructor for custom validation
    public CreateUserRequest {
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
