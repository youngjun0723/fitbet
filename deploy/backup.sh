#!/bin/sh
# DB 덤프 + 인증 사진을 backups/ 에 저장하고 14일 지난 백업은 지운다.
# 서버 crontab 예시 (매일 04:00):  0 4 * * * /home/ubuntu/fitbet/deploy/backup.sh >> /home/ubuntu/fitbet/backups/backup.log 2>&1
set -eu
cd "$(dirname "$0")/.."
TS=$(date +%Y%m%d-%H%M)
mkdir -p backups

# 1) DB: 컨테이너 안의 mariadb-dump 실행 → 압축해서 호스트에 저장
docker compose exec -T db sh -c 'mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" --single-transaction fitbet' \
  | gzip > "backups/db-$TS.sql.gz"

# 2) 사진: uploads 볼륨을 tar로 묶기
docker compose run --rm --no-deps --user root -v "$PWD/backups:/backup" --entrypoint tar app \
  czf "/backup/uploads-$TS.tgz" -C /data uploads

find backups -name '*.gz' -mtime +14 -delete
find backups -name '*.tgz' -mtime +14 -delete
echo "[$TS] backup done"
