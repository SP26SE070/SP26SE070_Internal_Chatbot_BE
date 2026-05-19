package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTenantProfileRequest {

    @Size(max = 500, message = "Address must be 500 characters or less")
    private String address;

    @Size(max = 255, message = "Website must be 255 characters or less")
    private String website;

    @Size(max = 50, message = "Company size is invalid")
    private String companySize;
}
