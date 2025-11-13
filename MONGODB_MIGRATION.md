# MongoDB Integration - What's New

## Summary

The system has been upgraded with MongoDB as the primary data store, while ElasticSearch is now used exclusively for vector embeddings and similarity search. This hybrid architecture provides better performance, data integrity, and query flexibility.

## What Changed

### 1. Added MongoDB Service
- **Image**: MongoDB 7.0
- **Port**: 27017
- **Database**: swift_templates
- **Storage**: Persistent volume (`mongo-data`)

### 2. New Data Models

#### MongoDB Documents (in `model/mongo/` package)
- `SwiftMessageDocument` - Full SWIFT message data
- `MessageTemplateDocument` - Rich template metadata with statistics
- `TransactionDocument` - Complete transaction lifecycle with audit trail

#### ElasticSearch Documents (updated)
- `VectorEmbedding` - Only vector embeddings (384 dimensions)

### 3. New Repositories

#### MongoDB Repositories (in `repository/mongo/` package)
- `SwiftMessageMongoRepository` - Message CRUD with business queries
- `MessageTemplateMongoRepository` - Template management
- `TransactionMongoRepository` - Transaction processing

#### ElasticSearch Repository (updated)
- `VectorEmbeddingRepository` - Vector operations only

### 4. New Services

- **`VectorService`**: Manages all ElasticSearch vector operations
  - Store/update/retrieve vectors
  - Calculate similarity
  - Find similar documents
  - Calculate centroids

- **`SwiftMessageServiceV2`**: New message service using MongoDB
  - Saves documents to MongoDB
  - Stores vectors in ElasticSearch
  - Maintains consistency between stores

### 5. New API Endpoints

#### V2 API (MongoDB + ElasticSearch)
- `POST /api/v2/messages` - Upload single message
- `POST /api/v2/messages/upload` - Upload file
- `GET /api/v2/messages` - Get all messages
- `GET /api/v2/messages/{id}` - Get message by ID
- `GET /api/v2/messages/type/{type}` - Get by message type
- `GET /api/v2/messages/status/{status}` - Get by status
- `DELETE /api/v2/messages/{id}` - Delete message
- `GET /api/v2/messages/statistics` - Get statistics

#### V1 API (Legacy - still works)
- All `/api/*` endpoints still functional
- Uses ElasticSearch only
- Deprecated but available for backward compatibility

## Benefits

### 1. Enhanced Data Model

**MongoDB allows rich, nested documents:**

```javascript
// Transaction with full audit trail
{
  "extractedData": { /* complex object */ },
  "userEnteredData": { /* complex object */ },
  "matchingDetails": {
    "alternativeMatches": [ /* array */ ],
    "fieldConfidences": { /* map */ },
    "warnings": [ /* array */ ]
  },
  "auditTrail": [
    {
      "timestamp": ISODate,
      "action": "STATUS_CHANGE",
      "details": "..."
    }
  ],
  "workflowState": {
    "currentStep": "approval",
    "completedSteps": [],
    "pendingSteps": []
  }
}
```

### 2. Better Query Capabilities

```javascript
// Complex MongoDB queries
db.transactions.find({
  "status": "PENDING",
  "matchConfidence": { "$lt": 0.9 },
  "amount": { "$gte": 100000 },
  "workflowState.currentStep": "approval"
})

// Aggregation pipelines
db.transactions.aggregate([
  { $match: { "status": "COMPLETED" } },
  { $group: {
      _id: "$currency",
      totalAmount: { $sum: "$amount" },
      count: { $sum: 1 }
    }
  },
  { $sort: { "totalAmount": -1 } }
])
```

### 3. ACID Transactions

```java
@Transactional
public void processTransaction(String messageId, String templateId) {
    // Multi-document transaction
    SwiftMessageDocument msg = messageRepo.findById(messageId).get();
    MessageTemplateDocument template = templateRepo.findById(templateId).get();

    TransactionDocument txn = createTransaction(msg, template);
    transactionRepo.save(txn);

    msg.setStatus(ProcessingStatus.PROCESSED);
    messageRepo.save(msg);

    template.getStatistics().setTotalMatches(
        template.getStatistics().getTotalMatches() + 1
    );
    templateRepo.save(template);

    // All or nothing - guaranteed consistency
}
```

### 4. Performance Optimization

- **MongoDB**: Fast queries on business fields (indexed)
- **ElasticSearch**: Fast vector similarity only
- **Reduced ES storage**: Only 384 floats per document vs full data
- **Better scaling**: Scale each database independently

### 5. Data Integrity

- MongoDB provides referential integrity
- Audit trails track all changes
- Change streams for real-time sync
- Automatic validation

## How to Use

### Starting the System

```bash
docker-compose up --build
```

This now starts:
1. MongoDB on port 27017
2. ElasticSearch on port 9200
3. Backend on port 8080
4. Frontend on port 3000

### Accessing Databases

#### MongoDB
```bash
# Connect with mongosh
docker exec -it swift-mongodb mongosh swift_templates

# List collections
show collections

# Query messages
db.swift_messages.find().pretty()

# Count documents
db.swift_messages.countDocuments()

# Find by criteria
db.swift_messages.find({ "messageType": "MT700", "status": "EMBEDDED" })
```

#### ElasticSearch
```bash
# Check cluster health
curl http://localhost:9200/_cluster/health

# Count vectors
curl http://localhost:9200/vector_embeddings/_count

# Search vectors
curl -X GET "http://localhost:9200/vector_embeddings/_search" \
  -H 'Content-Type: application/json' \
  -d '{ "query": { "match_all": {} } }'
```

### Using V2 APIs

The frontend automatically uses V2 APIs. If calling directly:

```bash
# Upload a message
curl -X POST http://localhost:8080/api/v2/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageType": "MT700",
    "rawContent": "...",
    "senderId": "BANKBEBB",
    "receiverId": "BANKUS33"
  }'

# Get all messages
curl http://localhost:8080/api/v2/messages

# Get statistics
curl http://localhost:8080/api/v2/messages/statistics
```

### MongoDB Query Examples

#### Find Messages Without Templates
```javascript
db.swift_messages.find({
  "templateId": null,
  "status": { "$in": ["EMBEDDED", "CLUSTERED"] }
})
```

#### Find High-Value Transactions
```javascript
db.transactions.find({
  "amount": { "$gte": 100000 },
  "currency": "USD",
  "status": "PENDING"
})
```

#### Template Usage Statistics
```javascript
db.message_templates.aggregate([
  {
    $project: {
      "messageType": 1,
      "confidence": 1,
      "matchRate": {
        $divide: [
          "$statistics.successfulMatches",
          "$statistics.totalMatches"
        ]
      }
    }
  },
  { $sort: { "matchRate": -1 } }
])
```

#### Audit Trail Query
```javascript
db.transactions.find({
  "auditTrail.performedBy": "jane.smith@bank.com"
})
```

## Code Examples

### Storing a Message

```java
@Autowired
private SwiftMessageServiceV2 messageService;

// Upload message - automatically stored in both MongoDB and ElasticSearch
SwiftMessageDocument message = messageService.uploadMessage(request);

// MongoDB: Full message data
// ElasticSearch: Only vector embedding
```

### Finding Similar Messages

```java
@Autowired
private VectorService vectorService;

// Find top 10 similar messages
List<SimilarityResult> similar = vectorService.findSimilarVectors(
    messageId,
    VectorEmbedding.DocumentType.MESSAGE.name(),
    10
);

// Get full message data from MongoDB
for (SimilarityResult result : similar) {
    SwiftMessageDocument msg = messageRepository
        .findById(result.getDocumentId())
        .get();

    System.out.println("Similarity: " + result.getSimilarity());
    System.out.println("Message: " + msg.getRawContent());
}
```

### Complex Transaction Query

```java
@Autowired
private TransactionMongoRepository transactionRepo;

// Find transactions needing review
@Query("{ " +
    "'matchConfidence': { $lt: 0.9 }, " +
    "'status': 'PENDING', " +
    "'amount': { $gte: ?0 } " +
"}")
List<TransactionDocument> findRequiringReview(BigDecimal minAmount);

// Use it
List<TransactionDocument> txns = transactionRepo.findRequiringReview(
    new BigDecimal("50000")
);
```

## Data Flow

### Message Upload Flow

```
1. User uploads message
   ↓
2. SwiftMessageServiceV2.uploadMessage()
   ↓
3. Parse SWIFT fields
   ↓
4. Create SwiftMessageDocument
   ↓
5. Save to MongoDB → Get ID
   ↓
6. VectorService.storeVector()
   ↓
7. Generate embedding (384 dimensions)
   ↓
8. Save VectorEmbedding to ElasticSearch
   ↓
9. Update message status to EMBEDDED
   ↓
10. Save updated document to MongoDB
```

### Template Matching Flow

```
1. Request to match message
   ↓
2. Get message vector from ElasticSearch
   ↓
3. VectorService.findSimilarVectors()
   ↓
4. Calculate cosine similarity to all template centroids
   ↓
5. Get top matching template ID
   ↓
6. Load full template data from MongoDB
   ↓
7. Extract fields based on template
   ↓
8. Create transaction in MongoDB
   ↓
9. Return match result
```

## Monitoring

### Check Data Consistency

```bash
# MongoDB count
docker exec swift-mongodb mongosh swift_templates --eval "db.swift_messages.countDocuments()"

# ElasticSearch count
curl -s http://localhost:9200/vector_embeddings/_count?q=documentType:MESSAGE | jq .count

# Should match!
```

### View MongoDB Logs

```bash
docker logs swift-mongodb -f
```

### View Application Logs

```bash
docker logs swift-backend -f | grep -i mongodb
```

## Troubleshooting

### MongoDB Not Starting

```bash
# Check logs
docker logs swift-mongodb

# Check health
docker exec swift-mongodb mongosh --eval "db.adminCommand('ping')"

# Restart
docker-compose restart mongodb
```

### Data Not Syncing

```bash
# Check backend logs for errors
docker logs swift-backend | grep -i error

# Verify MongoDB connection
docker exec swift-backend curl -s mongodb:27017

# Verify ElasticSearch connection
docker exec swift-backend curl -s elasticsearch:9200
```

### Clean Start

```bash
# Stop and remove all data
docker-compose down -v

# Start fresh
docker-compose up --build
```

## Migration Checklist

- [x] MongoDB added to docker-compose
- [x] MongoDB dependencies in pom.xml
- [x] MongoDB document models created
- [x] MongoDB repositories created
- [x] VectorService for ElasticSearch
- [x] SwiftMessageServiceV2 implemented
- [x] V2 API endpoints created
- [x] Frontend updated to use V2 APIs
- [ ] Template service migration (future)
- [ ] Transaction service migration (future)
- [ ] Complete v1 API deprecation (future)

## Next Steps

1. **Test the V2 APIs**
   - Upload messages via V2 endpoint
   - Verify data in MongoDB
   - Verify vectors in ElasticSearch

2. **Migrate Template Service**
   - Create TemplateExtractionServiceV2
   - Use MongoDB for template storage
   - Use ElasticSearch for centroid vectors

3. **Migrate Transaction Service**
   - Create TemplateMatchingServiceV2
   - Store transactions in MongoDB
   - Leverage vector similarity from ES

4. **Frontend Migration**
   - Update all components to use V2 APIs
   - Test thoroughly

5. **Deprecate V1**
   - Remove old controllers
   - Clean up ElasticSearch indexes
   - Update documentation

## Support

For issues or questions:
1. Check logs: `docker-compose logs -f`
2. Verify health: `docker-compose ps`
3. Review documentation: `ARCHITECTURE.md` and `MONGODB_ARCHITECTURE.md`
