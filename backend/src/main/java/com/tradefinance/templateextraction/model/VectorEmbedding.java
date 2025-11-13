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

/**
 * ElasticSearch document for storing vector embeddings only.
 * Actual data is stored in MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "vector_embeddings")
public class VectorEmbedding {

    @Id
    private String id; // Same as MongoDB document ID

    @Field(type = FieldType.Keyword)
    private String documentType; // MESSAGE, TEMPLATE, etc.

    @Field(type = FieldType.Keyword)
    private String referenceId; // MongoDB document ID

    @Field(type = FieldType.Dense_Vector, dims = 384)
    private float[] embedding;

    @Field(type = FieldType.Integer)
    private Integer clusterId;

    @Field(type = FieldType.Text)
    private String contentPreview; // First 200 chars for debugging

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime updatedAt;

    public enum DocumentType {
        MESSAGE,
        TEMPLATE,
        TRANSACTION
    }
}
