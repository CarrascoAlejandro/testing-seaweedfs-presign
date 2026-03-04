#!/bin/sh
set -e

FILER="http://seaweedfs-filer:8888"
WRITE_SECRET="your-filer-write-secret-change-me"

echo "Waiting for filer..."
until curl -sf "$FILER/" > /dev/null 2>&1; do
  sleep 2
done

# Generate a minimal HS256 JWT inline.
# Header and payload are base64url-encoded, then signed with HMAC-SHA256.
header=$(printf '{"alg":"HS256","typ":"JWT"}' | base64 | tr '+/' '-_' | tr -d '=\n')
exp=$(( $(date +%s) + 60 ))
payload=$(printf '{"exp":%d}' "$exp" | base64 | tr '+/' '-_' | tr -d '=\n')
sig=$(printf '%s.%s' "$header" "$payload" \
  | openssl dgst -sha256 -hmac "$WRITE_SECRET" -binary \
  | base64 | tr '+/' '-_' | tr -d '=\n')
TOKEN="${header}.${payload}.${sig}"

curl -sf -X POST "$FILER/buckets/inbox/" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: " || echo "inbox may already exist"

curl -sf -X POST "$FILER/buckets/processed/" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: " || echo "processed may already exist"

echo "Filer directories ready."
