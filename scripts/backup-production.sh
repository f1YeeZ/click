#!/usr/bin/env bash
set -Eeuo pipefail

# Production backup for the Clicker Demo Compose deployment.
# Override COMPOSE_PROJECT_DIR, BACKUP_DIR, or RETENTION_DAYS when needed.

umask 077

COMPOSE_PROJECT_DIR="${COMPOSE_PROJECT_DIR:-/opt/clicker_demo}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/clicker_demo}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
LOCK_FILE="${LOCK_FILE:-/run/lock/clicker-demo-backup.lock}"

die() {
  printf 'backup failed: %s\n' "$*" >&2
  exit 1
}

[[ "$BACKUP_DIR" = /* ]] || die "BACKUP_DIR must be an absolute path"
[[ "$BACKUP_DIR" != "/" && "$BACKUP_DIR" != "$COMPOSE_PROJECT_DIR" ]] || die "refusing to use a broad backup path"
[[ "$RETENTION_DAYS" =~ ^[0-9]+$ && "$RETENTION_DAYS" -ge 1 ]] || die "RETENTION_DAYS must be a positive integer"

command -v docker >/dev/null 2>&1 || die "docker is not installed"
docker compose version >/dev/null 2>&1 || die "Docker Compose Plugin is not installed"
command -v flock >/dev/null 2>&1 || die "flock is not installed"
command -v sha256sum >/dev/null 2>&1 || die "sha256sum is not installed"
[[ -d "$COMPOSE_PROJECT_DIR" ]] || die "compose directory does not exist: $COMPOSE_PROJECT_DIR"

mkdir -p "$BACKUP_DIR" "$(dirname "$LOCK_FILE")"
chmod 700 "$BACKUP_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || die "another backup is already running"

cd "$COMPOSE_PROJECT_DIR"
db_container="$(docker compose ps -q database)"
[[ -n "$db_container" ]] || die "database container is not running"
[[ "$(docker inspect -f '{{.State.Running}}' "$db_container")" == "true" ]] || die "database container is not running"

stamp="$(date -u +%Y%m%d-%H%M%S)"
tmp_dir="$(mktemp -d "$BACKUP_DIR/.incomplete.XXXXXX")"
final_dir="$BACKUP_DIR/$stamp"
[[ ! -e "$final_dir" ]] || die "backup directory already exists: $final_dir"

cleanup() {
  if [[ -n "${tmp_dir:-}" && -d "$tmp_dir" ]]; then
    rm -rf -- "$tmp_dir"
  fi
}
trap cleanup EXIT

db_user="$(docker compose exec -T database sh -c 'printf "%s" "$POSTGRES_USER"')"
db_name="$(docker compose exec -T database sh -c 'printf "%s" "$POSTGRES_DB"')"
[[ -n "$db_user" && -n "$db_name" ]] || die "database container is missing POSTGRES_USER or POSTGRES_DB"

db_file="$tmp_dir/database.dump"
docker compose exec -T database sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "$db_file"
[[ -s "$db_file" ]] || die "database dump is empty"

backend_container="$(docker compose ps -q backend)"
[[ -n "$backend_container" ]] || die "backend container is not running"
image_volume="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data/mouse-images"}}{{.Name}}{{end}}{{end}}' "$backend_container")"
[[ -n "$image_volume" ]] || die "could not find the mouse-images Docker volume"

image_file="$tmp_dir/mouse-images.tar.gz"
docker run --rm --network none \
  -v "$image_volume:/source:ro" \
  -v "$tmp_dir:/backup" \
  alpine:3.20 tar -czf /backup/mouse-images.tar.gz -C /source .
[[ -s "$image_file" ]] || die "image archive is empty"

cat > "$tmp_dir/manifest.txt" <<EOF
created_at_utc=$stamp
compose_project_dir=$COMPOSE_PROJECT_DIR
database=$db_name
database_user=$db_user
image_volume=$image_volume
EOF

sha256sum "$tmp_dir/database.dump" "$tmp_dir/mouse-images.tar.gz" > "$tmp_dir/SHA256SUMS"
chmod 600 "$tmp_dir"/*
mv -- "$tmp_dir" "$final_dir"
tmp_dir=""

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime "+$RETENTION_DAYS" -print -exec rm -rf -- {} +

printf 'backup complete: %s\n' "$final_dir"
