#!/usr/bin/env bash
set -euo pipefail
umask 077
cd /opt/academic-support
exec 9>/var/lock/academic-support-backup.lock
flock -n 9 || exit 0
mkdir -p backups
file="backups/academic-support-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
docker compose --env-file .env -f docker-compose.server.yml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --routines --triggers --events "$MYSQL_DATABASE"' | gzip > "$file"
/opt/academic-support/tools/venv/bin/python /opt/academic-support/tools/storage_admin.py upload-backup --site production --file "$file"
/opt/academic-support/tools/venv/bin/python /opt/academic-support/tools/storage_admin.py prune --site production --days 30 --keep 7
# Local dumps contain student data; keep only seven days after verified cloud upload.
find /opt/academic-support/backups -maxdepth 1 -name 'academic-support-*.sql.gz' -type f -mtime +7 -delete
