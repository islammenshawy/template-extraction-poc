#!/bin/bash

# Get auth token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@swift.com","password":"admin123"}' | jq -r '.token')

echo "Fetching all templates with confidence scores..."
echo ""

curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/templates?size=100" | \
  jq '{
    totalElements,
    templates: [.content[] | {
      id,
      buyerId,
      sellerId,
      messageType,
      confidence: (.confidence * 100 | round),
      messageCount
    }]
  }'
