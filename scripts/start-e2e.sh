#!/bin/bash
echo "🚀 Starting WireMock Proxy E2E Environment (Dockerized)..."

# 1. Start Everything via Docker Compose
echo "📦 Building and Starting Docker containers..."
# --build ensures we rebuild the app 
docker-compose up -d --build
if [ $? -ne 0 ]; then
    echo "❌ Failed to start docker-compose."
    exit 1
fi

echo "⏳ Waiting for App to be ready..."
sleep 10

echo "✅ Environment is UP."
echo "👉 WireMock Proxy: http://localhost:8222"
echo "👉 App API: http://localhost:8333"
echo "👉 Target Service: test_precondition (AWS)"
echo "👉 DynamoDB Admin: http://localhost:8001"
echo "👉 Frontend: open web/index.html in your browser"

# No need to run gradle manually anymore
