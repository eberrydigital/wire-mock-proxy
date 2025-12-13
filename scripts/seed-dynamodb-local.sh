#!/usr/bin/env zsh
# Seed DynamoDB Local with placeholder tables for Phase 1 reproducibility.
# Requires: awscli v2 installed and configured to use local endpoint.
# Usage:
#   ./scripts/seed-dynamodb-local.sh

set -euo pipefail

ENDPOINT_URL=${ENDPOINT_URL:-http://localhost:8000}
AWS_REGION=${AWS_REGION:-us-east-1}

function ensure_aws() {
  if ! command -v aws >/dev/null 2>&1; then
    echo "Error: aws CLI not found. Install from https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html" >&2
    exit 1
  fi
}

function create_table() {
  local name=$1
  local key=${2:-"sessionId"}
  echo "Creating table ${name}..."
  aws dynamodb create-table \
    --table-name "${name}" \
    --attribute-definitions AttributeName=${key},AttributeType=S \
    --key-schema AttributeName=${key},KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --table-class STANDARD \
    --endpoint-url "${ENDPOINT_URL}" \
    --region "${AWS_REGION}" >/dev/null || true
}

function enable_ttl() {
  local name=$1
  local attr=${2:-"ttl"}
  echo "Enabling TTL on ${name} (${attr})..."
  aws dynamodb update-time-to-live \
    --table-name "${name}" \
    --time-to-live-specification Enabled=true,AttributeName=${attr} \
    --endpoint-url "${ENDPOINT_URL}" \
    --region "${AWS_REGION}" >/dev/null || true
}

ensure_aws

# Phase 1 placeholders
create_table "sessions" "sessionId"
create_table "proxy-traffic" "sessionId"

# Optional TTL on traffic
enable_ttl "proxy-traffic" "ttl"

echo "Done. DynamoDB Local seeded at ${ENDPOINT_URL}."

