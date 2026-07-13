#!/bin/sh
set -eu

VERSION="${1:-}"
IMAGE_ARCHIVE="${2:-}"

if [ -z "$VERSION" ]; then
    echo "Usage: sh update.sh <version> [image-tar]" >&2
    exit 2
fi

case "$VERSION" in
    *[!0-9A-Za-z._-]*)
        echo "Invalid version: $VERSION" >&2
        exit 2
        ;;
esac

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

if [ ! -f .env ]; then
    echo ".env does not exist. Configure it from .env.example first." >&2
    exit 1
fi

if [ -n "$IMAGE_ARCHIVE" ] && [ ! -f "$IMAGE_ARCHIVE" ]; then
    echo "Image archive not found: $IMAGE_ARCHIVE" >&2
    exit 1
fi

if ! docker compose ps --status running --services | grep -qx 'mysql'; then
    echo "The MySQL service is not running; refusing to upgrade without a database backup." >&2
    exit 1
fi

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="backup/$TIMESTAMP"
mkdir -p "$BACKUP_DIR"

echo "Backing up MySQL to $BACKUP_DIR/forklift_erp.sql"
docker compose exec -T mysql sh -c \
    'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" forklift_erp' \
    > "$BACKUP_DIR/forklift_erp.sql"

if [ -d data/uploads ]; then
    echo "Backing up uploads to $BACKUP_DIR/uploads.tar.gz"
    tar -czf "$BACKUP_DIR/uploads.tar.gz" -C data uploads
fi

ENV_TMP=".env.$TIMESTAMP.tmp"
awk -v version="$VERSION" '
    BEGIN { updated = 0 }
    /^ERP_VERSION=/ { print "ERP_VERSION=" version; updated = 1; next }
    { print }
    END { if (!updated) print "ERP_VERSION=" version }
' .env > "$ENV_TMP"
mv "$ENV_TMP" .env

if [ -n "$IMAGE_ARCHIVE" ]; then
    echo "Loading $IMAGE_ARCHIVE"
    docker load --input "$IMAGE_ARCHIVE"
else
    echo "Pulling the configured ERP image tag $VERSION"
    docker compose pull app
fi

echo "Recreating the ERP application container"
docker compose up -d --no-deps app
docker compose ps app

echo "Upgrade completed. Verify /actuator/health, login, attachments, and business pages."
echo "Backup: $BACKUP_DIR"
