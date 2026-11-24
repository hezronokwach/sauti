#!/usr/bin/env bash
# Dumps the database, encrypts it with a public key and uploads it. The unencrypted dump is only
# ever in a pipe, never on disk. Login sessions are left out on purpose.
set -euo pipefail

: "${BACKUP_DATABASE_URL:?needs the database owner connection URL}"
: "${BACKUP_AGE_RECIPIENT:?needs the age public key}"
: "${BACKUP_S3_ENDPOINT:?needs the storage endpoint}"
: "${BACKUP_BUCKET:?needs the private backup bucket}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-35}"

started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
file="sauti-$(date -u +%Y%m%dT%H%M%SZ).dump.age"
finished_ok=false

aws_s3() { aws --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"; }

record() {
    psql "$BACKUP_DATABASE_URL" -q -v ON_ERROR_STOP=1 \
        -v started="$started" -v status="$1" -v file="$2" -v size="$3" <<'SQL'
insert into backup_run (started_at, finished_at, status, file_name, size_bytes)
values (:'started', now(), :'status', nullif(:'file', ''), nullif(:'size', '')::bigint);
SQL
}

on_exit() {
    if [ "$finished_ok" != true ]; then
        echo "Backup FAILED" >&2
        record FAILED "" "" || echo "Could not record the failure" >&2
    fi
}
trap on_exit EXIT

pg_dump --format=custom --no-owner --no-privileges \
    --exclude-table-data=spring_session --exclude-table-data=spring_session_attributes \
    "$BACKUP_DATABASE_URL" \
    | age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" \
    | aws_s3 s3 cp - "s3://$BACKUP_BUCKET/$file"

size="$(aws_s3 s3api head-object --bucket "$BACKUP_BUCKET" --key "$file" \
    --query ContentLength --output text)"
[ "$size" -gt 0 ]
record SUCCESS "$file" "$size"
finished_ok=true
echo "Backup stored: $file ($size bytes)"

# Only after a good backup, and only files this script made.
cutoff="$(date -u -d "$KEEP_DAYS days ago" +%Y-%m-%dT%H:%M:%SZ)"
old="$(aws_s3 s3api list-objects-v2 --bucket "$BACKUP_BUCKET" --prefix sauti- \
    --query "Contents[?LastModified<'$cutoff'].Key" --output text || true)"
for key in $old; do
    case "$key" in
        sauti-*.dump.age) aws_s3 s3 rm "s3://$BACKUP_BUCKET/$key" || echo "Could not remove $key" >&2 ;;
    esac
done
