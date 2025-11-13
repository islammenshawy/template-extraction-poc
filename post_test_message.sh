#!/bin/bash

# Wait a bit more for data loading to complete
echo "Waiting for templates to be available..."
sleep 20

# Get a new token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@swift.com","password":"admin123"}' | jq -r '.token')

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
  echo "Failed to get auth token. Creating admin user..."
  curl -s -X POST http://localhost:8080/api/auth/register \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@swift.com","password":"admin123","fullName":"Admin User"}'

  TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@swift.com","password":"admin123"}' | jq -r '.token')
fi

echo "Token obtained: ${TOKEN:0:20}..."

# Check if templates exist
TEMPLATE_COUNT=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/templates" | jq -r '.totalElements // 0')

echo "Templates available: $TEMPLATE_COUNT"

if [ "$TEMPLATE_COUNT" == "0" ]; then
  echo "No templates available yet. Waiting for template extraction..."
  sleep 30
fi

# Post a test message with slight differences
echo ""
echo "Posting test SWIFT MT700 message with slight differences..."
echo "============================================================"

RESPONSE=$(curl -s -X POST http://localhost:8080/api/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "rawContent": ":20:TESTLC987654\n:23:ISSUANCE OF A DOCUMENTARY CREDIT\n:31C:251231\n:40A:IRREVOCABLE\n:31D:251215\n:50:ACME CORPORATION USA\n123 MAIN STREET\nNEW YORK NY 10001\n:59:ABC TRADING COMPANY\nSHANGHAI COMMERCIAL DISTRICT\nSHANGHAI CHINA\n:32B:USD 85000.00\n:39A:05/05\n:41D:DEUTSCHE BANK AG FRANKFURT\n:42C:DRAFTS AT SIGHT\n:43P:PARTIAL SHIPMENTS PROHIBITED\n:43T:TRANSSHIPMENT NOT ALLOWED\n:44A:COMMODITY: INDUSTRIAL MACHINERY\nQUANTITY: 25 UNITS\nUNIT PRICE: USD 3400.00\n:44E:SHANGHAI PORT, CHINA\n:44F:NEW YORK PORT, USA\n:44C:251220\n:45A:COMMERCIAL INVOICE IN TRIPLICATE\nPACKING LIST IN DUPLICATE\nCERTIFICATE OF ORIGIN\nINSURANCE CERTIFICATE\nBILL OF LADING\n:46A:MACHINERY PARTS AS PER PROFORMA\nINVOICE NO. PI-2024-8765\n:47A:ADDITIONAL CONDITIONS:\nINSPECTION CERTIFICATE REQUIRED\nCERTIFICATE OF QUALITY REQUIRED\n:71B:ALL BANKING CHARGES OUTSIDE\nOPENING BANK ARE FOR ACCOUNT\nOF BENEFICIARY\n:48:PERIOD FOR PRESENTATION 15 DAYS\n:49:CONFIRMATION INSTRUCTIONS IF ANY",
    "messageType": "MT700",
    "sender": "DEUTDEFF",
    "receiver": "BKCHUS33",
    "buyerId": "ACMECORPUSA",
    "sellerId": "ABCTRADCN"
  }')

echo "$RESPONSE" | jq '.'

# Extract transaction ID
TRANS_ID=$(echo "$RESPONSE" | jq -r '.transactionId // .id // empty')

if [ -n "$TRANS_ID" ]; then
  echo ""
  echo "✓ Message posted successfully!"
  echo "Transaction ID: $TRANS_ID"
  echo ""
  echo "LLM Comparison:"
  echo "============================================================"
  echo "$RESPONSE" | jq -r '.llmComparison // "No LLM comparison available"'
  echo ""
  echo "Matched Template ID: $(echo "$RESPONSE" | jq -r '.templateId // "N/A"')"
  echo "Match Confidence: $(echo "$RESPONSE" | jq -r '.matchConfidence // "N/A"')%"
else
  echo "Failed to post message or extract transaction ID"
  echo "Response: $RESPONSE"
fi
