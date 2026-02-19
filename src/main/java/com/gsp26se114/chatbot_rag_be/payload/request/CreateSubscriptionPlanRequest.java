package com.gsp26se114.chatbot_rag_be.payload.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionPlanRequest {
    
    @NotBlank(message = "Code không được để trống")
    @Size(max = 50, message = "Code không được vượt quá 50 ký tự")
    private String code; // TRIAL, STARTER, STANDARD, ENTERPRISE
    
    @NotBlank(message = "Tên plan không được để trống")
    @Size(max = 100, message = "Tên không được vượt quá 100 ký tự")
    private String name;
    
    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;
    
    @NotNull(message = "Giá theo tháng không được để trống")
    @DecimalMin(value = "0.0", message = "Giá phải >= 0")
    private BigDecimal monthlyPrice;
    
    @NotNull(message = "Giá theo quý không được để trống")
    @DecimalMin(value = "0.0", message = "Giá phải >= 0")
    private BigDecimal quarterlyPrice;
    
    @NotNull(message = "Giá theo năm không được để trống")
    @DecimalMin(value = "0.0", message = "Giá phải >= 0")
    private BigDecimal yearlyPrice;
    
    @NotNull(message = "Số user tối đa không được để trống")
    @Min(value = 1, message = "Số user tối đa phải >= 1")
    private Integer maxUsers;
    
    @NotNull(message = "Số document tối đa không được để trống")
    @Min(value = 0, message = "Số document tối đa phải >= 0")
    private Integer maxDocuments;
    
    @NotNull(message = "Dung lượng tối đa không được để trống")
    @Min(value = 1, message = "Dung lượng tối đa phải >= 1 GB")
    private Integer maxStorageGb;
    
    @NotNull(message = "Số API call tối đa không được để trống")
    @Min(value = 0, message = "Số API call tối đa phải >= 0")
    private Integer maxApiCalls;
    
    @NotNull(message = "Số chatbot request tối đa không được để trống")
    @Min(value = 0, message = "Số chatbot request tối đa phải >= 0")
    private Integer maxChatbotRequests;
    
    @NotNull(message = "Số RAG document tối đa không được để trống")
    @Min(value = 0, message = "Số RAG document tối đa phải >= 0")
    private Integer maxRagDocuments;
    
    @NotNull(message = "Số AI token tối đa không được để trống")
    @Min(value = 0, message = "Số AI token tối đa phải >= 0")
    private Integer maxAiTokens;
    
    @NotNull(message = "Context window size không được để trống")
    @Min(value = 1024, message = "Context window phải >= 1024")
    private Integer contextWindowTokens;
    
    @NotNull(message = "RAG chunk size không được để trống")
    @Min(value = 256, message = "RAG chunk size phải >= 256")
    private Integer ragChunkSize;
    
    @Size(max = 100, message = "AI model không được vượt quá 100 ký tự")
    private String aiModel;
    
    @Size(max = 100, message = "Embedding model không được vượt quá 100 ký tự")
    private String embeddingModel;
    
    @NotNull(message = "Enable RAG không được để trống")
    private Boolean enableRag;
    
    @NotNull(message = "Is trial không được để trống")
    private Boolean isTrial;
    
    @Min(value = 1, message = "Trial days phải >= 1 nếu là trial plan")
    private Integer trialDays;
    
    @NotNull(message = "Display order không được để trống")
    @Min(value = 0, message = "Display order phải >= 0")
    private Integer displayOrder;
    
    @Size(max = 500, message = "Features không được vượt quá 500 ký tự")
    private String features;
}
