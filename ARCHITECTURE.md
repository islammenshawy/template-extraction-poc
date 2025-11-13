# System Architecture

## Overview

The SWIFT Template Extraction System uses a **hybrid database architecture** combining MongoDB and ElasticSearch to leverage the strengths of each database:

- **MongoDB**: Primary storage for transactional data, document management, and complex queries
- **ElasticSearch**: Specialized storage for vector embeddings and similarity search operations

## Database Architecture

### MongoDB - Primary Data Store

MongoDB stores all structured business data with rich document models:

#### Collections

**1. swift_messages**
```javascript
{
  "_id": ObjectId,
  "messageType": "MT700",
  "rawContent": "...",
  "parsedFields": {
    "20": "LC123456789",
    "32B": "USD100000,00",
    // ... more fields
  },
  "senderId": "BANKBEBB",
  "receiverId": "BANKUS33",
  "timestamp": ISODate,
  "templateId": "template_id",
  "clusterId": 5,
  "status": "TEMPLATE_MATCHED",
  "notes": "...",
  "createdAt": ISODate,
  "updatedAt": ISODate,
  "createdBy": "system"
}
```

**Use cases:**
- Store complete SWIFT message data
- Track processing status and workflow
- Store parsed field values for queries
- Maintain audit trail

**2. message_templates**
```javascript
{
  "_id": ObjectId,
  "messageType": "MT700",
  "templateContent": "...",
  "variableFields": [
    {
      "fieldName": "documentaryCreditNumber",
      "fieldTag": "20",
      "type": "ALPHANUMERIC",
      "sampleValues": ["LC123", "LC456"],
      "required": true,
      "pattern": "LC[0-9]+",
      "description": "Documentary Credit Number"
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
    "lastMatchedAt": ISODate
  },
  "sampleMessageIds": ["msg_1", "msg_2"],
  "createdAt": ISODate,
  "lastUpdated": ISODate,
  "createdBy": "system"
}
```

**Use cases:**
- Store template structure and metadata
- Track template usage statistics
- Manage variable field definitions
- Link to sample messages

**3. transactions**
```javascript
{
  "_id": ObjectId,
  "swiftMessageId": "message_id",
  "templateId": "template_id",
  "messageType": "MT700",
  "extractedData": {
    "documentaryCreditNumber": "LC123456789",
    "amount": 100000.00,
    "currency": "USD"
    // ... more fields
  },
  "userEnteredData": {
    "approvalDate": ISODate,
    "approvedBy": "user@example.com"
  },
  "validatedData": {
    // Merged and validated data
  },
  "matchConfidence": 0.95,
  "matchedTemplateIds": ["template_1", "template_2"],
  "matchingDetails": {
    "primaryTemplateId": "template_1",
    "alternativeMatches": [
      {
        "templateId": "template_2",
        "confidence": 0.88,
        "reason": "Similar structure"
      }
    ],
    "fieldConfidences": {
      "20": 0.99,
      "32B": 0.97
    },
    "warnings": ["Low confidence on field 44A"],
    "suggestions": ["Review shipment date"]
  },
  "status": "VALIDATED",
  "buyerId": "BUYER_123",
  "sellerId": "SELLER_456",
  "documentaryCreditNumber": "LC123456789",
  "amount": 100000.00,
  "currency": "USD",
  "shipmentDate": ISODate,
  "expiryDate": ISODate,
  "auditTrail": [
    {
      "timestamp": ISODate,
      "action": "STATUS_CHANGE",
      "performedBy": "user@example.com",
      "details": "...",
      "previousStatus": "PENDING",
      "newStatus": "VALIDATED"
    }
  ],
  "workflowState": {
    "currentStep": "approval",
    "completedSteps": ["validation", "review"],
    "pendingSteps": ["approval", "disbursement"],
    "stepData": {},
    "lastTransitionAt": ISODate
  },
  "processedAt": ISODate,
  "processedBy": "system"
}
```

**Use cases:**
- Store complete transaction lifecycle
- Track multiple template matches
- Maintain detailed audit trail
- Support complex workflow states
- Store business data (amounts, dates, parties)

### ElasticSearch - Vector Storage

ElasticSearch stores only vector embeddings for similarity operations:

#### Index: vector_embeddings

```json
{
  "_id": "document_id",
  "documentType": "MESSAGE",
  "referenceId": "mongodb_document_id",
  "embedding": [0.123, -0.456, ...], // 384 dimensions
  "clusterId": 5,
  "contentPreview": "First 200 chars...",
  "createdAt": "2025-11-10T...",
  "updatedAt": "2025-11-10T..."
}
```

**Use cases:**
- Store 384-dimensional vector embeddings
- Perform similarity searches
- Support clustering operations
- Quick vector comparisons

## Data Flow

### 1. Message Upload Flow

```
User Upload
    ↓
SwiftMessageServiceV2
    ↓
┌─────────────────┐
│ MongoDB         │ ← Save message document
│ swift_messages  │
└─────────────────┘
    ↓
VectorService
    ↓
EmbeddingService (generate 384-dim vector)
    ↓
┌─────────────────────┐
│ ElasticSearch       │ ← Save vector embedding
│ vector_embeddings   │
└─────────────────────┘
```

### 2. Template Extraction Flow

```
Extract Templates Request
    ↓
TemplateExtractionService
    ↓
Get messages from MongoDB
    ↓
Get vectors from ElasticSearch
    ↓
ClusteringService (K-Means on vectors)
    ↓
Group messages by cluster
    ↓
Extract common structure
    ↓
┌─────────────────────┐
│ MongoDB             │ ← Save template
│ message_templates   │
└─────────────────────┘
    ↓
VectorService
    ↓
Calculate centroid embedding
    ↓
┌─────────────────────┐
│ ElasticSearch       │ ← Save template centroid
│ vector_embeddings   │
└─────────────────────┘
```

### 3. Template Matching Flow

```
Match Message Request
    ↓
TemplateMatchingService
    ↓
Get message vector from ElasticSearch
    ↓
VectorService.findSimilarVectors()
    ↓
Calculate cosine similarity to all templates
    ↓
Get best matching template from MongoDB
    ↓
Extract fields based on template
    ↓
┌─────────────────┐
│ MongoDB         │ ← Save transaction
│ transactions    │
└─────────────────┘
```

## Benefits of Hybrid Architecture

### MongoDB Advantages
1. **Rich Document Model**: Store complex nested structures
2. **ACID Transactions**: Support for multi-document transactions
3. **Flexible Schema**: Easy to evolve data model
4. **Powerful Queries**: Complex aggregations and filters
5. **Audit Trail**: Track all changes with timestamps
6. **Relationships**: Link documents across collections
7. **Indexing**: Fast queries on business fields

### ElasticSearch Advantages
1. **Vector Operations**: Optimized for dense vector storage
2. **Similarity Search**: Fast cosine similarity calculations
3. **Clustering Support**: Efficient vector grouping
4. **Scalability**: Horizontal scaling for large datasets
5. **Real-time**: Low-latency vector operations

## Service Layer Architecture

### VectorService
- Manages all ElasticSearch vector operations
- Stores and retrieves embeddings
- Performs similarity searches
- Calculates cluster centroids

### SwiftMessageServiceV2
- Primary interface for message operations
- Saves documents to MongoDB
- Delegates vector operations to VectorService
- Maintains consistency between stores

### TemplateExtractionService
- Orchestrates template creation
- Retrieves data from MongoDB
- Uses VectorService for clustering
- Saves results to both stores

### TemplateMatchingService
- Matches messages to templates
- Uses VectorService for similarity
- Retrieves full data from MongoDB
- Creates transactions in MongoDB

## Data Consistency

### Write Operations
1. Write to MongoDB first (authoritative store)
2. On success, write to ElasticSearch
3. On ES failure, log and continue (can rebuild)

### Read Operations
1. For business queries: MongoDB
2. For similarity operations: ElasticSearch
3. For complete data: Join using document IDs

### Synchronization
- Document ID is same in both stores
- ElasticSearch `referenceId` points to MongoDB `_id`
- Can rebuild ElasticSearch from MongoDB if needed

## Scalability Considerations

### MongoDB Scaling
- Shard by messageType or date ranges
- Replica sets for read scalability
- Indexes on frequently queried fields

### ElasticSearch Scaling
- Shard by documentType or clusterId
- Multiple replicas for availability
- Use routing for co-location

## Monitoring & Maintenance

### Health Checks
```bash
# MongoDB
curl http://localhost:27017

# ElasticSearch
curl http://localhost:9200/_cluster/health
```

### Data Sync Verification
- Count documents in MongoDB vs vectors in ES
- Verify critical documents have vectors
- Periodic reconciliation jobs

### Backup Strategy
- MongoDB: Regular snapshots of collections
- ElasticSearch: Can be rebuilt from MongoDB
- Store embeddings cache for quick recovery

## API Versioning

- **v1 APIs** (`/api/*`): Legacy, ElasticSearch-only (deprecated)
- **v2 APIs** (`/api/v2/*`): Current, MongoDB + ElasticSearch

## Performance Optimization

### MongoDB Indexes
```javascript
// Messages
db.swift_messages.createIndex({ messageType: 1, status: 1 })
db.swift_messages.createIndex({ timestamp: -1 })
db.swift_messages.createIndex({ senderId: 1, receiverId: 1 })

// Templates
db.message_templates.createIndex({ messageType: 1, confidence: -1 })
db.message_templates.createIndex({ clusterId: 1 })

// Transactions
db.transactions.createIndex({ status: 1, processedAt: -1 })
db.transactions.createIndex({ templateId: 1 })
db.transactions.createIndex({ documentaryCreditNumber: 1 })
```

### ElasticSearch Settings
```json
{
  "number_of_shards": 3,
  "number_of_replicas": 1,
  "refresh_interval": "5s"
}
```

## Future Enhancements

1. **Event Sourcing**: Track all state changes
2. **CQRS**: Separate read/write models
3. **Change Data Capture**: Sync MongoDB to ES automatically
4. **Graph Database**: Add Neo4j for relationship analysis
5. **Time Series**: Add TimescaleDB for analytics
6. **Cache Layer**: Add Redis for hot data
