#!/usr/bin/env bash
# Restores a backup into a fresh, empty database. It refuses a database that already has tables.
set -euo pipefail

: "${RESTORE_DATABASE_URL:?needs the fresh database owner connection URL}"
: "${BACKUP_AGE_IDENTITY_FILE:?needs the file holding the age private key}"
: "${BACKUP_S3_ENDPOINT:?needs the storage endpoint}"
: "${BACKUP_BUCKET:?needs the backup bucket}"

aws_s3() { aws --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"; }

tables="$(psql "$RESTORE_DATABASE_URL" -Atc \
    "select count(*) from pg_tables where schemaname = 'public'")"
if [ "$tables" != "0" ]; then
    echo "Refusing to restore: the target database already has $tables tables" >&2
    exit 1
fi

file="${BACKUP_FILE:-$(aws_s3 s3api list-objects-v2 --bucket "$BACKUP_BUCKET" --prefix sauti- \
    --query 'sort_by(Contents, &LastModified)[-1].Key' --output text)}"
case "$file" in
    sauti-*.dump.age) ;;
    *) echo "No backup file found" >&2; exit 1 ;;
esac

echo "Restoring $file"
aws_s3 s3 cp "s3://$BACKUP_BUCKET/$file" - \
    | age --decrypt --identity "$BACKUP_AGE_IDENTITY_FILE" \
    | pg_restore --no-owner --no-privileges --exit-on-error --single-transaction \
        --dbname "$RESTORE_DATABASE_URL"
psql "$RESTORE_DATABASE_URL" -q -v ON_ERROR_STOP=1 -f "$(dirname "$0")/grants.sql"
echo "Restored $file"
