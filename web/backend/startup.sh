#!/bin/bash

# backend/startup.sh - Docker startup script with health validation

set -e  # Exit on any error

echo "🚀 Starting Puzzle Server with Health Validation..."
echo "📍 Environment: ${NODE_ENV:-development}"
echo "🔧 Port: ${PORT:-8080}"

# Start the server in background
echo "🚀 Starting Node.js server..."
node server.js &
SERVER_PID=$!

# Function to cleanup on exit
cleanup() {
    echo "🧹 Cleaning up..."
    if kill -0 $SERVER_PID 2>/dev/null; then
        echo "🛑 Stopping server (PID: $SERVER_PID)"
        kill $SERVER_PID
        wait $SERVER_PID 2>/dev/null || true
    fi
}

# Set up signal handlers
trap cleanup EXIT INT TERM

# Wait for server to start
echo "⏳ Waiting for server to initialize..."
sleep 15

# Health check configuration
MAX_ATTEMPTS=6
ATTEMPT=1
HEALTH_CHECK_URL="http://localhost:${PORT:-8080}/api/health/comprehensive?force=true"

echo "🏥 Running comprehensive health checks..."

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    echo "🔍 Health check attempt $ATTEMPT/$MAX_ATTEMPTS..."
    
    # Check if server is still running
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "❌ Server process died unexpectedly!"
        exit 1
    fi
    
    # Run health check
    if curl -f -s --max-time 30 "$HEALTH_CHECK_URL" > /tmp/health_result.json; then
        # Parse the health check result
        HEALTHY=$(cat /tmp/health_result.json | grep -o '"healthy":[^,]*' | cut -d':' -f2 | tr -d ' ')
        STATUS=$(cat /tmp/health_result.json | grep -o '"status":"[^"]*"' | cut -d':' -f2 | tr -d '"')
        MESSAGE=$(cat /tmp/health_result.json | grep -o '"message":"[^"]*"' | cut -d':' -f2 | tr -d '"')
        
        echo "📊 Health Status: $STATUS"
        echo "💬 Message: $MESSAGE"
        
        if [ "$HEALTHY" = "true" ]; then
            echo "✅ Health check passed!"
            echo "🎉 System is healthy and ready for traffic!"
            echo "📋 Health check details:"
            cat /tmp/health_result.json | grep -o '"summary":{[^}]*}' || echo "Summary not available"
            
            # Clean up temp file
            rm -f /tmp/health_result.json
            
            echo "🔄 Server running in foreground mode..."
            # Bring server to foreground and wait
            wait $SERVER_PID
            exit 0
        else
            echo "⚠️ Health check returned unhealthy status"
            cat /tmp/health_result.json
        fi
    else
        echo "❌ Health check HTTP request failed"
    fi
    
    if [ $ATTEMPT -lt $MAX_ATTEMPTS ]; then
        echo "⏳ Waiting 10 seconds before retry..."
        sleep 10
    fi
    
    ATTEMPT=$((ATTEMPT + 1))
done

echo "💥 Health check failed after $MAX_ATTEMPTS attempts!"
echo "🔍 Final server status check..."

if kill -0 $SERVER_PID 2>/dev/null; then
    echo "🛑 Server is still running but unhealthy - stopping it"
    kill $SERVER_PID
    wait $SERVER_PID 2>/dev/null || true
else
    echo "💀 Server process is not running"
fi

# Show last few lines of server output for debugging
echo "📋 Last server output (for debugging):"
tail -n 20 /tmp/server.log 2>/dev/null || echo "No server log available"

exit 1