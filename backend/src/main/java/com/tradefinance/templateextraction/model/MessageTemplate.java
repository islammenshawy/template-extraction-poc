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
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "message_templates")
public class MessageTemplate {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String messageType;

    @Field(type = FieldType.Keyword)
    private String buyerId;  // Sender/Buyer in trading pair

    @Field(type = FieldType.Keyword)
    private String sellerId;  // Receiver/Seller in trading pair

    @Field(type = FieldType.Text)
    private String templateContent;

    @Field(type = FieldType.Object)
    private List<VariableField> variableFields;

    @Field(type = FieldType.Integer)
    private Integer clusterId;

    @Field(type = FieldType.Dense_Vector, dims = 384)
    private float[] centroidEmbedding;

    @Field(type = FieldType.Integer)
    private Integer messageCount;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime lastUpdated;

    @Field(type = FieldType.Keyword)
    private String createdBy;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private Double confidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariableField {
        private String fieldName;
        private String fieldTag;
        private FieldType type;
        private List<String> sampleValues;
        private boolean required;

        public enum FieldType {
            ALPHANUMERIC,
            NUMERIC,
            DATE,
            AMOUNT,
            CODE,
            TEXT
        }
    }
}
