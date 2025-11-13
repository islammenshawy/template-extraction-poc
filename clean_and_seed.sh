#!/bin/bash

echo "============================================================"
echo "Clean and Seed Script - Template Extraction POC"
echo "============================================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Stop backend
echo -e "${YELLOW}Step 1: Stopping backend service...${NC}"
docker stop swift-backend 2>/dev/null || echo "Backend not running"
echo -e "${GREEN}✓ Backend stopped${NC}"
echo ""

# Step 2: Clear MongoDB
echo -e "${YELLOW}Step 2: Clearing MongoDB data...${NC}"
docker exec swift-mongodb mongosh --quiet swift_templates --eval '
  db.swift_messages.deleteMany({});
  db.message_templates.deleteMany({});
  db.transactions.deleteMany({});
  print("MongoDB cleared");
' 2>/dev/null
echo -e "${GREEN}✓ MongoDB cleared${NC}"
echo ""

# Step 3: Clear ElasticSearch
echo -e "${YELLOW}Step 3: Clearing ElasticSearch indices...${NC}"
curl -s -X DELETE "http://localhost:9200/vector_embeddings" >/dev/null 2>&1
curl -s -X DELETE "http://localhost:9200/message_templates" >/dev/null 2>&1
curl -s -X DELETE "http://localhost:9200/transactions" >/dev/null 2>&1
curl -s -X DELETE "http://localhost:9200/swift_messages" >/dev/null 2>&1
echo -e "${GREEN}✓ ElasticSearch cleared${NC}"
echo ""

# Step 4: Remove old backend container and image
echo -e "${YELLOW}Step 4: Removing old backend container and image...${NC}"
docker rm swift-backend 2>/dev/null || echo "Container already removed"
docker image rm template-extraction-poc-backend 2>/dev/null || echo "Image already removed"
echo -e "${GREEN}✓ Old container and image removed${NC}"
echo ""

# Step 5: Rebuild backend without cache
echo -e "${YELLOW}Step 5: Rebuilding backend (this may take a few minutes)...${NC}"
docker-compose build --no-cache backend
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Backend rebuilt successfully${NC}"
else
    echo -e "${RED}✗ Backend build failed${NC}"
    exit 1
fi
echo ""

# Step 6: Start backend
echo -e "${YELLOW}Step 6: Starting backend service...${NC}"
docker-compose up -d backend
sleep 15  # Wait for backend to be fully ready
echo -e "${GREEN}✓ Backend started${NC}"
echo ""

# Step 7: Verify backend is healthy
echo -e "${YELLOW}Step 7: Verifying backend health...${NC}"
response=$(curl -s -w "%{http_code}" http://localhost:8080/api/templates -o /dev/null)
if [ "$response" = "200" ]; then
    echo -e "${GREEN}✓ Backend is healthy and responding${NC}"
else
    echo -e "${RED}✗ Backend health check failed (HTTP $response)${NC}"
    echo "Check logs with: docker logs swift-backend"
    exit 1
fi
echo ""

# Step 8: Load test data
echo -e "${YELLOW}Step 8: Loading test data...${NC}"
cd "$(dirname "$0")"
python3 load_swift_data.py | tail -15
echo -e "${GREEN}✓ Test data loaded${NC}"
echo ""

# Step 9: Verify data
echo -e "${YELLOW}Step 9: Verifying loaded data...${NC}"
stats=$(curl -s http://localhost:8080/api/v2/messages/statistics)
total=$(echo $stats | python3 -c "import sys, json; print(json.load(sys.stdin)['totalMessages'])" 2>/dev/null)
embedded=$(echo $stats | python3 -c "import sys, json; print(json.load(sys.stdin)['byStatus'].get('EMBEDDED', 0))" 2>/dev/null)

echo "Total messages loaded: $total"
echo "Messages ready for clustering: $embedded"
echo -e "${GREEN}✓ Data verification complete${NC}"
echo ""

echo "============================================================"
echo -e "${GREEN}✓ Clean and seed completed successfully!${NC}"
echo "============================================================"
echo ""
echo "Next steps:"
echo "1. Open http://localhost:3000 to access the frontend"
echo "2. Go to the Templates page"
echo "3. Click 'Extract Templates' to test field-pattern clustering"
echo "4. View generated templates and match transactions"
echo ""
echo "Useful commands:"
echo "  - View backend logs: docker logs -f swift-backend"
echo "  - Check message stats: curl http://localhost:8080/api/v2/messages/statistics"
echo "  - Extract templates: curl -X POST http://localhost:8080/api/templates/extract"
echo ""
