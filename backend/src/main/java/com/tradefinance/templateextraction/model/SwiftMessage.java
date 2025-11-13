package com.tradefinance.templateextraction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "swift_messages")
public class SwiftMessage {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String messageType; // MT700, MT710, MT720, etc.

    @Field(type = FieldType.Text)
    private String rawContent;

    @Field(type = FieldType.Object)
    private Map<String, String> parsedFields;

    @Field(type = FieldType.Keyword)
    private String senderId;

    @Field(type = FieldType.Keyword)
    private String receiverId;

    @Field(type = FieldType.Date)
    private LocalDateTime timestamp;

    @Field(type = FieldType.Keyword)
    private String templateId;

    @Field(type = FieldType.Integer)
    private Integer clusterId;

    @Field(type = FieldType.Dense_Vector, dims = 384)
    private float[] embedding;

    @Field(type = FieldType.Keyword)
    private ProcessingStatus status;

    @Field(type = FieldType.Text)
    private String notes;

    public enum ProcessingStatus {
        NEW,
        EMBEDDED,
        CLUSTERED,
        TEMPLATE_MATCHED,
        PROCESSED,
        ERROR
    }
}
