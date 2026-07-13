#!/usr/bin/env sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:53990}"
COOKIE_FILE="${TMPDIR:-/tmp}/zhilu-delivery-cookie.$$"
trap 'rm -f "$COOKIE_FILE"' EXIT

curl -fsS "$BASE_URL/actuator/health" | grep -q '"status":"UP"'
curl -fsS -c "$COOKIE_FILE" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123"}' \
  "$BASE_URL/api/v1/auth/login" | grep -q '"username":"admin"'
curl -fsS -b "$COOKIE_FILE" "$BASE_URL/api/v1/dashboard/summary" | grep -q '"totalProjects"'
curl -fsS -b "$COOKIE_FILE" "$BASE_URL/api/v1/projects" | grep -q 'PRJ-26001'
curl -fsS -b "$COOKIE_FILE" "$BASE_URL/api/v1/knowledge?publishedOnly=true" | grep -q '月末关账'
curl -fsS -b "$COOKIE_FILE" "$BASE_URL/api/v1/resources/team" | grep -q '陈曦'

echo "Smoke test passed: $BASE_URL"
