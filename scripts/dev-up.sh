#!/usr/bin/env zsh
# Start local dev stack: Docker deps (DynamoDB Local, WireMock), seed tables, run the app.
# Usage:
#   ./scripts/dev-up.sh

set -euo pipefail

# Bring up Docker services
if ! docker compose up -d; then
  echo "docker compose failed" >&2
  exit 1
fi

# Seed DynamoDB Local tables
if ! ./scripts/seed-dynamodb-local.sh; then
  echo "Seeding DynamoDB Local failed" >&2
  exit 1
fi

# Change it so that we use either .env or .env.test depending on the environment
export PORT=${PORT:-8080}
export DYN_ALLOWED_PORTS=${DYN_ALLOWED_PORTS:-80,443}
# Example service map; change omni mapping to your target
export SERVICE_MAP=${SERVICE_MAP:-omni=https://api.test.eberry.digital}

# Run the app (foreground)
./gradlew run

