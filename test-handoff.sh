#!/usr/bin/env bash

# Sample curl commands for the /handoff endpoint
BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=== Test 1: Successful handoff ==="
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "${BASE_URL}/handoff" \
  -H "Content-Type: application/json" \
  -H "X-PIN: 123456" \
  -d '{"message": "hello", "agent": "brushpass"}'

echo ""
echo "=== Test 2: Missing X-PIN header (expect 403) ==="
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "${BASE_URL}/handoff" \
  -H "Content-Type: application/json" \
  -d '{"message": "hello"}'

echo ""
echo "=== Test 3: Empty body (expect 400) ==="
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "${BASE_URL}/handoff" \
  -H "Content-Type: application/json" \
  -H "X-PIN: 123456"

echo ""
echo "=== Test 4: Malformed JSON (expect 400) ==="
curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "${BASE_URL}/handoff" \
  -H "Content-Type: application/json" \
  -H "X-PIN: 123456" \
  -d 'not valid json'
