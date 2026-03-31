#!/bin/bash

# backend/startup-simple.sh - Simpler startup script

echo "🚀 Starting Puzzle Server..."

# Start server in background
node server.js &
SERVER_PID=$!

# Wait for startup
echo "⏳ Waiting for server startup..."
sleep 10

# Single health check
echo "🏥 Running health check..."
if curl -f http://localhost:8080/api/health/comprehensive?force=true; then
    echo "✅ Health check passed! Server ready."
    # Bring to foreground
    wait $SERVER_PID
else
    echo "❌ Health check failed!"
    kill $SERVER_PID 2>/dev/null || true
    exit 1
fi