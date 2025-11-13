package com.tradefinance.templateextraction.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "swift_messages")
public class SwiftMessageDocument {

    @Id
    private String id;

    private String messageType;
    private String rawContent;
    private Map<String, String> parsedFields;
    private String senderId;
    private String receiverId;
    private LocalDateTime timestamp;
    private String templateId;
    private Integer clusterId;
    private ProcessingStatus status;
    private String notes;

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public enum ProcessingStatus {
        NEW,
        EMBEDDED,
        CLUSTERED,
        TEMPLATE_MATCHED,
        PROCESSED,
        ERROR
    }
}
