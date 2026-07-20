#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

STACK_DIR=/opt/outline-stack
BACKUP_ROOT=/opt/outline-backups
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$BACKUP_ROOT/$stamp"
mkdir -p "$target"
cd "$STACK_DIR"

compose=(docker compose --env-file .env -f docker-compose.ecs.yml)
"${compose[@]}" exec -T postgres pg_dump -U outline -d outline --format=custom > "$target/outline.pgdump"
"${compose[@]}" exec -T postgres pg_restore -l < "$target/outline.pgdump" >/dev/null

docker pull alpine:3.23 >/dev/null
for volume in outline-data dex-data caddy-data; do
  docker run --rm \
    -v "outline-stack_${volume}:/source:ro" \
    -v "$target:/backup" \
    alpine:3.23 sh -c "tar -C /source -czf /backup/${volume}.tar.gz ."
  tar -tzf "$target/${volume}.tar.gz" >/dev/null
done

tar -czf "$target/config-and-secrets.tar.gz" \
  .env docker-compose.ecs.yml Caddyfile dex/config.yaml dex/password.hash
tar -tzf "$target/config-and-secrets.tar.gz" >/dev/null
(cd "$target" && sha256sum outline.pgdump ./*.tar.gz > SHA256SUMS && sha256sum -c SHA256SUMS)

mapfile -t backups < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '20*T*Z' | sort)
if (( ${#backups[@]} > 7 )); then
  printf '%s\0' "${backups[@]:0:${#backups[@]}-7}" | xargs -0 rm -rf --
fi
echo "Outline backup completed: $target"
