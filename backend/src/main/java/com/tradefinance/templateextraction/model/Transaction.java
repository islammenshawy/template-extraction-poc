package com.tradefinance.templateextraction.model;

import com.tradefinance.templateextraction.dto.StructuredAnalysis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "transactions")
public class Transaction {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String swiftMessageId;

    @Field(type = FieldType.Keyword)
    private String templateId;

    @Field(type = FieldType.Keyword)
    private String messageType;

    @Field(type = FieldType.Object)
    private Map<String, Object> extractedData;

    @Field(type = FieldType.Object)
    private Map<String, Object> userEnteredData;

    @Field(type = FieldType.Double)
    private Double matchConfidence;

    @Field(type = FieldType.Object)
    private MatchingDetails matchingDetails;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime processedAt;

    @Field(type = FieldType.Keyword)
    private String processedBy;

    @Field(type = FieldType.Keyword)
    private TransactionStatus status;

    @Field(type = FieldType.Text)
    private String remarks;

    @Field(type = FieldType.Text)
    private String llmComparison;

    @Field(type = FieldType.Object)
    private StructuredAnalysis structuredAnalysis;

    @Field(type = FieldType.Keyword)
    private String buyerId;

    @Field(type = FieldType.Keyword)
    private String sellerId;

    @Field(type = FieldType.Object)
    private Map<String, String> metadata;

    public enum TransactionStatus {
        PENDING,
        MATCHED,
        VALIDATED,
        APPROVED,
        REJECTED,
        COMPLETED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchingDetails {
        private String primaryTemplateId;
        private Map<String, Double> fieldConfidences;
        private List<String> warnings;
        private List<String> suggestions;
    }
}
