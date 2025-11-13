#!/bin/bash

# Get auth token
echo "Getting authentication token..."
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@swift.com","password":"admin123"}' | jq -r '.token')

echo "Token obtained: ${TOKEN:0:30}..."

# Extract templates for a trading pair
echo "Extracting templates for CITIBANK → DEUTDEFF..."
curl -s -X POST "http://localhost:8080/api/templates/extract?buyerId=CITIBANK&sellerId=DEUTDEFF" \
  -H "Authorization: Bearer $TOKEN" | jq '{templateCount, processingTime}'

# Wait a bit for extraction to complete
sleep 5

# Post a test message with slight differences
echo ""
echo "Posting test MT700 message with SLIGHT differences from template..."
echo "===================================================================="
echo ""

# This message is similar to typical MT700s but with different amounts, dates, and parties
RESPONSE=$(curl -s -X POST http://localhost:8080/api/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "rawContent": ":20:TESTLC999888\n:23:ISSUANCE OF A DOCUMENTARY CREDIT\n:31C:260131\n:40A:IRREVOCABLE\n:31D:251220\n:50:GLOBAL TECH INC\n456 OAK AVENUE\nSAN FRANCISCO CA 94102\n:59:SHANGHAI MANUFACTURING LTD\nPUDONG DISTRICT\nSHANGHAI CHINA\n:32B:USD 125000.00\n:39A:10/10\n:41D:CITIBANK NA NEW YORK\n:42C:DRAFTS AT SIGHT\n:43P:PARTIAL SHIPMENTS ALLOWED\n:43T:TRANSSHIPMENT PERMITTED\n:44A:COMMODITY: ELECTRONIC COMPONENTS\nQUANTITY: 500 UNITS\nUNIT PRICE: USD 250.00\n:44E:SHANGHAI PORT, CHINA\n:44F:LOS ANGELES PORT, USA\n:44C:260115\n:45A:COMMERCIAL INVOICE IN QUADRUPLICATE\nPACKING LIST IN TRIPLICATE\nCERTIFICATE OF ORIGIN\nINSURANCE POLICY\nOCEAN BILL OF LADING\n:46A:ELECTRONIC PARTS AS PER PURCHASE\nORDER NO. PO-2025-5432\n:47A:SPECIAL CONDITIONS:\nQUALITY INSPECTION REQUIRED\nTEST REPORT MANDATORY\n:71B:ALL BANK CHARGES OUTSIDE ISSUING\nBANK FOR BENEFICIARY ACCOUNT\n:48:DOCUMENTS MUST BE PRESENTED WITHIN 21 DAYS\n:49:WITHOUT CONFIRMATION",
    "messageType": "MT700",
    "sender": "CITIUS33",
    "receiver": "DEUTDEFF",
    "buyerId": "CITIBANK",
    "sellerId": "DEUTDEFF"
  }')

echo "API Response:"
echo "$RESPONSE" | jq '.'

# Extract and display key information
TRANS_ID=$(echo "$RESPONSE" | jq -r '.transactionId // .id // empty')

if [ -n "$TRANS_ID" ]; then
  echo ""
  echo "============================================================"
  echo "✓ Message Posted Successfully!"
  echo "============================================================"
  echo ""
  echo "Transaction ID: $TRANS_ID"
  echo "Matched Template: $(echo "$RESPONSE" | jq -r '.templateId // "N/A"')"
  echo "Match Confidence: $(echo "$RESPONSE" | jq -r '.matchConfidence // "N/A"')%"
  echo ""
  echo "------------------------------------------------------------"
  echo "LLM COMPARISON ANALYSIS:"
  echo "------------------------------------------------------------"
  echo "$RESPONSE" | jq -r '.llmComparison // "No LLM comparison available (Azure OpenAI not configured)"'
  echo ""
  echo "============================================================"
  echo ""
  echo "Key Differences in this test message:"
  echo "  • Amount: USD 125,000 (vs typical 75,000-100,000)"
  echo "  • Commodity: Electronic Components (vs Machinery)"
  echo "  • Quantity/Price: 500 units @ $250 each"
  echo "  • Partial shipments: ALLOWED (vs typical PROHIBITED)"
  echo "  • Transshipment: PERMITTED (vs typical NOT ALLOWED)"
  echo "  • Documents: QUADRUPLICATE invoice (vs typical TRIPLICATE)"
  echo "  • Presentation period: 21 days (vs typical 15 days)"
  echo ""
fi
