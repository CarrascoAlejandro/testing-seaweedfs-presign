#!/bin/sh
set -e

echo "=== SeaweedFS init: waiting for S3 to be ready ==="
until aws --endpoint-url http://seaweedfs:8333 --region us-east-1 s3 ls > /dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done

echo "=== Creating buckets ==="
aws --endpoint-url http://seaweedfs:8333 --region us-east-1 s3 mb s3://inbox    2>/dev/null && echo "  inbox created"    || echo "  inbox already exists"
aws --endpoint-url http://seaweedfs:8333 --region us-east-1 s3 mb s3://processed 2>/dev/null && echo "  processed created" || echo "  processed already exists"

echo "=== Done ==="
