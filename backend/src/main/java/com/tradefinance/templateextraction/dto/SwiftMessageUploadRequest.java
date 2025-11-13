package com.tradefinance.templateextraction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwiftMessageUploadRequest {

    @NotBlank(message = "Message type is required")
    @Pattern(regexp = "MT7[0-9]{2}", message = "Message type must be in format MT7XX")
    private String messageType;

    @NotBlank(message = "Raw content is required")
    private String rawContent;

    private String senderId;

    private String receiverId;

    private String notes;
}
