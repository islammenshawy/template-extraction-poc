#!/bin/bash

# Get auth token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@swift.com","password":"admin123"}' | jq -r '.token')

echo "Extracting templates for all trading pairs..."
echo ""

# Extract templates for each pair
echo "1. Extracting for CITIBANK → DEUTDEFF..."
curl -s -X POST "http://localhost:8080/api/templates/extract?buyerId=CITIBANK&sellerId=DEUTDEFF" \
  -H "Authorization: Bearer $TOKEN" | jq '{templateCount, processingTime}'

echo ""
echo "2. Extracting for JPMORGAN → BNPPARFR..."
curl -s -X POST "http://localhost:8080/api/templates/extract?buyerId=JPMORGAN&sellerId=BNPPARFR" \
  -H "Authorization: Bearer $TOKEN" | jq '{templateCount, processingTime}'

echo ""
echo "3. Extracting for BOFA → HSBCGB2L..."
curl -s -X POST "http://localhost:8080/api/templates/extract?buyerId=BOFA&sellerId=HSBCGB2L" \
  -H "Authorization: Bearer $TOKEN" | jq '{templateCount, processingTime}'

echo ""
echo "4. Extracting for DBSSGSG → CITIBANK..."
curl -s -X POST "http://localhost:8080/api/templates/extract?buyerId=DBSSGSG&sellerId=CITIBANK" \
  -H "Authorization: Bearer $TOKEN" | jq '{templateCount, processingTime}'

echo ""
echo "5. Extracting for MIZUHOJP → JPMORGAN..."
curl -s -X POST "http://localhost:8080/api/templates/extract?buyerId=MIZUHOJP&sellerId=JPMORGAN" \
  -H "Authorization: Bearer $TOKEN" | jq '{templateCount, processingTime}'

echo ""
echo "6. Extracting for ICBCCNBJ → BOFA..."
curl -s -X POST "http://localhost:8080/api/templates/extract?buyerId=ICBCCNBJ&sellerId=BOFA" \
  -H "Authorization: Bearer $TOKEN" | jq '{templateCount, processingTime}'

echo ""
echo "========================================================================"
echo "Template extraction complete!"
echo "========================================================================"
