# MongoDB + ElasticSearch Architecture

## Overview

The system now uses a **hybrid database architecture** that combines:
- **MongoDB** for primary data storage (transactional data, business logic)
- **ElasticSearch** for vector embeddings and similarity search only

This separation provides:
1. **Better Performance**: Each database does what it's optimized for
2. **Data Integrity**: MongoDB provides ACID transactions
3. **Scalability**: Independent scaling of storage and vector operations
4. **Cost Efficiency**: Only vectors in expensive ElasticSearch storage
5. **Query Flexibility**: Rich MongoDB queries + fast vector search

## Architecture Comparison

### Previous (ElasticSearch Only)
```
┌─────────────────────────────────┐
│      ElasticSearch              │
│  ┌─────────────────────────┐   │
│  │ Messages (full data +   │   │
│  │  384-dim vectors)       │   │
│  ├─────────────────────────┤   │
│  │ Templates (full data +  │   │
│  │  centroid vectors)      │   │
│  ├─────────────────────────┤   │
│  │ Transactions            │   │
│  │  (full data)            │   │
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

### New (MongoDB + ElasticSearch)
```
┌──────────────────────┐    ┌─────────────────────┐
│     MongoDB          │    │   ElasticSearch     │
│  ┌────────────────┐ │    │  ┌────────────────┐│
│  │ Messages       │ │    │  │ Vector         ││
│  │ (full data)    │ │◄───┼──┤ Embeddings     ││
│  ├────────────────┤ │    │  │ (384-dim only) ││
│  │ Templates      │ │    │  └────────────────┘│
│  │ (full data)    │ │    │                     │
│  ├────────────────┤ │    │  Fast similarity    │
│  │ Transactions   │ │    │  search only        │
│  │ (full data +   │ │    │                     │
│  │  audit trail)  │ │    └─────────────────────┘
│  └────────────────┘ │
│                      │
│  Rich queries +      │
│  ACID transactions   │
└──────────────────────┘
```

## Data Models

### MongoDB Collections

#### 1. swift_messages
**Purpose**: Store complete SWIFT message data with metadata

**Document Structure**:
```javascript
{
  "_id": "msg_123abc",
  "messageType": "MT700",
  "rawContent": "{1:F01BANKBEBBAXXX...}",
  "parsedFields": {
    "20": "LC123456789",
    "32B": "USD100000,00",
    "50": "APPLICANT NAME...",
    "59": "BENEFICIARY NAME..."
  },
  "senderId": "BANKBEBB",
  "receiverId": "BANKUS33",
  "timestamp": ISODate("2025-11-10T12:00:00Z"),
  "templateId": "template_xyz",
  "clusterId": 5,
  "status": "TEMPLATE_MATCHED",
  "notes": "Initial LC for contract 2025-001",
  "createdAt": ISODate("2025-11-10T12:00:00Z"),
  "updatedAt": ISODate("2025-11-10T12:05:00Z"),
  "createdBy": "system",
  "updatedBy": "user@example.com"
}
```

**Indexes**:
```javascript
db.swift_messages.createIndex({ "messageType": 1, "status": 1 })
db.swift_messages.createIndex({ "timestamp": -1 })
db.swift_messages.createIndex({ "senderId": 1, "receiverId": 1 })
db.swift_messages.createIndex({ "templateId": 1 })
db.swift_messages.createIndex({ "clusterId": 1 })
```

#### 2. message_templates
**Purpose**: Store extracted templates with rich metadata and statistics

**Document Structure**:
```javascript
{
  "_id": "template_xyz",
  "messageType": "MT700",
  "templateContent": "...",
  "variableFields": [
    {
      "fieldName": "documentaryCreditNumber",
      "fieldTag": "20",
      "type": "ALPHANUMERIC",
      "sampleValues": ["LC123456", "LC789012"],
      "required": true,
      "pattern": "LC[0-9]+",
      "description": "Documentary Credit Number"
    },
    {
      "fieldName": "amount",
      "fieldTag": "32B",
      "type": "AMOUNT",
      "sampleValues": ["USD100000,00", "EUR50000,00"],
      "required": true,
      "pattern": "[A-Z]{3}[0-9]+,[0-9]{2}",
      "description": "Currency and Amount"
    }
  ],
  "clusterId": 5,
  "messageCount": 150,
  "confidence": 0.92,
  "statistics": {
    "totalMatches": 145,
    "successfulMatches": 140,
    "failedMatches": 5,
    "averageConfidence": 0.91,
    "lastMatchedAt": ISODate("2025-11-10T11:50:00Z")
  },
  "sampleMessageIds": ["msg_123", "msg_456", "msg_789"],
  "createdAt": ISODate("2025-11-01T10:00:00Z"),
  "lastUpdated": ISODate("2025-11-10T11:50:00Z"),
  "createdBy": "system",
  "description": "Standard documentary credit template"
}
```

**Indexes**:
```javascript
db.message_templates.createIndex({ "messageType": 1, "confidence": -1 })
db.message_templates.createIndex({ "clusterId": 1 }, { unique: true })
db.message_templates.createIndex({ "statistics.lastMatchedAt": -1 })
```

#### 3. transactions
**Purpose**: Store transaction processing data with full audit trail

**Document Structure**:
```javascript
{
  "_id": "txn_456def",
  "swiftMessageId": "msg_123abc",
  "templateId": "template_xyz",
  "messageType": "MT700",

  // Extracted data from message
  "extractedData": {
    "documentaryCreditNumber": "LC123456789",
    "amount": 100000.00,
    "currency": "USD",
    "applicant": "ACME Corp",
    "beneficiary": "XYZ Industries",
    "expiryDate": "2025-12-31"
  },

  // User-entered data
  "userEnteredData": {
    "approvalDate": ISODate("2025-11-10T14:00:00Z"),
    "approvedBy": "john.doe@bank.com",
    "internalReference": "INT-2025-001"
  },

  // Validated and merged data
  "validatedData": {
    // Combination of extracted + user data after validation
  },

  // Matching details
  "matchConfidence": 0.95,
  "matchedTemplateIds": ["template_xyz", "template_abc"],
  "matchingDetails": {
    "primaryTemplateId": "template_xyz",
    "alternativeMatches": [
      {
        "templateId": "template_abc",
        "confidence": 0.88,
        "reason": "Similar structure but older version"
      }
    ],
    "fieldConfidences": {
      "20": 0.99,
      "32B": 0.97,
      "50": 0.94,
      "59": 0.96
    },
    "warnings": [
      "Low confidence on field 44A (loading port)"
    ],
    "suggestions": [
      "Review shipment date for accuracy"
    ]
  },

  // Business data (denormalized for queries)
  "buyerId": "BUYER_123",
  "sellerId": "SELLER_456",
  "documentaryCreditNumber": "LC123456789",
  "amount": NumberDecimal("100000.00"),
  "currency": "USD",
  "shipmentDate": ISODate("2025-12-15T00:00:00Z"),
  "expiryDate": ISODate("2025-12-31T23:59:59Z"),

  // Status and workflow
  "status": "VALIDATED",
  "workflowState": {
    "currentStep": "approval_pending",
    "completedSteps": ["data_extraction", "validation", "review"],
    "pendingSteps": ["approval", "disbursement"],
    "stepData": {
      "reviewer": "jane.smith@bank.com",
      "reviewDate": ISODate("2025-11-10T13:00:00Z")
    },
    "lastTransitionAt": ISODate("2025-11-10T13:30:00Z")
  },

  // Audit trail
  "auditTrail": [
    {
      "timestamp": ISODate("2025-11-10T12:05:00Z"),
      "action": "CREATED",
      "performedBy": "system",
      "details": "Transaction created from message match",
      "previousStatus": null,
      "newStatus": "PENDING"
    },
    {
      "timestamp": ISODate("2025-11-10T13:00:00Z"),
      "action": "STATUS_CHANGE",
      "performedBy": "jane.smith@bank.com",
      "details": "Reviewed and validated transaction data",
      "previousStatus": "PENDING",
      "newStatus": "VALIDATED"
    }
  ],

  // Metadata
  "metadata": {
    "source": "swift_network",
    "priority": "high",
    "region": "EMEA"
  },

  "processedAt": ISODate("2025-11-10T12:05:00Z"),
  "processedBy": "system",
  "remarks": "Standard LC processing"
}
```

**Indexes**:
```javascript
db.transactions.createIndex({ "status": 1, "processedAt": -1 })
db.transactions.createIndex({ "templateId": 1 })
db.transactions.createIndex({ "documentaryCreditNumber": 1 })
db.transactions.createIndex({ "buyerId": 1, "sellerId": 1 })
db.transactions.createIndex({ "amount": 1, "currency": 1 })
db.transactions.createIndex({ "workflowState.currentStep": 1 })
```

### ElasticSearch Index

#### vector_embeddings
**Purpose**: Store ONLY vector embeddings for similarity search

**Document Structure**:
```json
{
  "_id": "msg_123abc",
  "documentType": "MESSAGE",
  "referenceId": "msg_123abc",
  "embedding": [
    0.123, -0.456, 0.789, ..., 0.321
  ],
  "clusterId": 5,
  "contentPreview": "First 200 characters of content for debugging...",
  "createdAt": "2025-11-10T12:00:00Z",
  "updatedAt": "2025-11-10T12:00:00Z"
}
```

**Mapping**:
```json
{
  "mappings": {
    "properties": {
      "documentType": { "type": "keyword" },
      "referenceId": { "type": "keyword" },
      "embedding": {
        "type": "dense_vector",
        "dims": 384,
        "index": true,
        "similarity": "cosine"
      },
      "clusterId": { "type": "integer" },
      "contentPreview": { "type": "text" },
      "createdAt": { "type": "date" },
      "updatedAt": { "type": "date" }
    }
  }
}
```

## API Endpoints

### V2 API (MongoDB + ElasticSearch)

All V2 endpoints use the new architecture:

**Messages**: `/api/v2/messages/*`
- Uses `SwiftMessageServiceV2`
- Stores data in MongoDB
- Stores vectors in ElasticSearch

**Templates**: `/api/v2/templates/*` (to be implemented)
**Transactions**: `/api/v2/transactions/*` (to be implemented)

### V1 API (Legacy - ElasticSearch only)

Legacy endpoints still available at `/api/*` but deprecated.

## Benefits

### 1. Separation of Concerns
- **MongoDB**: Business logic, transactional data, audit trails
- **ElasticSearch**: Vector operations and similarity only

### 2. Better Query Capabilities
MongoDB provides:
- Complex aggregation pipelines
- ACID transactions
- Rich query operators
- Joins via $lookup
- Text search on business fields

### 3. Enhanced Data Model
- Nested documents (audit trail, workflow states)
- Arrays of subdocuments (variable fields, alternatives)
- Flexible schema evolution
- Built-in versioning support

### 4. Performance Optimization
- MongoDB indexes on business fields
- ElasticSearch optimized for vectors only
- Reduced ES storage costs
- Faster non-vector queries

### 5. Data Integrity
- MongoDB transactions ensure consistency
- Referential integrity between collections
- Automatic validation rules
- Change streams for real-time sync

## Migration Strategy

### Phase 1: Dual Write (Current)
- Write to both MongoDB and ElasticSearch
- V2 APIs use new architecture
- V1 APIs still work (legacy)

### Phase 2: Read Migration
- Frontend updated to use V2 APIs
- Monitor performance
- Gradual rollout

### Phase 3: Deprecation
- Disable V1 APIs
- Remove old ES documents
- Keep only vectors in ES

## Development Guide

### Adding New Fields to MongoDB

1. Update the document model:
```java
@Data
@Document(collection = "swift_messages")
public class SwiftMessageDocument {
    // ... existing fields
    private String newField;
}
```

2. No migration needed (schemaless)
3. Add index if frequently queried

### Querying MongoDB

```java
// Simple query
List<SwiftMessageDocument> messages =
    messageRepository.findByMessageType("MT700");

// Complex query
@Query("{ 'status': ?0, 'amount': { $gte: ?1 } }")
List<TransactionDocument> findByStatusAndMinAmount(
    TransactionStatus status, BigDecimal minAmount);

// Aggregation
Aggregation agg = Aggregation.newAggregation(
    match(Criteria.where("messageType").is("MT700")),
    group("templateId").count().as("total"),
    sort(Sort.Direction.DESC, "total")
);
```

### Vector Operations

```java
// Store vector
vectorService.storeVector(
    documentId,
    DocumentType.MESSAGE.name(),
    content,
    clusterId
);

// Find similar
List<SimilarityResult> similar =
    vectorService.findSimilarVectors(
        queryEmbedding,
        DocumentType.MESSAGE.name(),
        10  // top 10
    );

// Get centroid
float[] centroid =
    vectorService.calculateClusterCentroid(clusterId);
```

## Monitoring

### MongoDB Metrics
```bash
# Connection status
db.serverStatus().connections

# Collection stats
db.swift_messages.stats()

# Index usage
db.swift_messages.aggregate([
  { $indexStats: {} }
])
```

### ElasticSearch Metrics
```bash
# Cluster health
GET /_cluster/health

# Index stats
GET /vector_embeddings/_stats

# Vector count
GET /vector_embeddings/_count
```

### Data Sync Check
```bash
# Compare counts
MongoDB: db.swift_messages.count()
ES: GET /vector_embeddings/_count?q=documentType:MESSAGE
```

## Backup and Recovery

### MongoDB Backup
```bash
# Dump all collections
mongodump --uri="mongodb://localhost:27017/swift_templates" --out=/backup

# Restore
mongorestore --uri="mongodb://localhost:27017/swift_templates" /backup
```

### ElasticSearch Backup
Vectors can be regenerated from MongoDB:
```java
// Rebuild all vectors
List<SwiftMessageDocument> messages = messageRepository.findAll();
messages.forEach(msg -> {
    vectorService.storeVector(
        msg.getId(),
        DocumentType.MESSAGE.name(),
        msg.getRawContent(),
        msg.getClusterId()
    );
});
```

## Best Practices

1. **Always write to MongoDB first** - it's the source of truth
2. **Use MongoDB for business queries** - it's faster for structured data
3. **Use ElasticSearch only for vectors** - don't duplicate business data
4. **Keep document IDs synchronized** - same ID in both stores
5. **Handle ES failures gracefully** - system can work without vectors temporarily
6. **Regular sync checks** - ensure consistency
7. **Index strategically** - only fields you query frequently
8. **Use aggregations** - for complex reports and analytics
