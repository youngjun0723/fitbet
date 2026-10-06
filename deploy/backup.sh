#!/bin/sh
# DB 덤프 + 인증 사진을 backups/ 에 저장하고 14일 지난 백업은 지운다.
#   Linux crontab (매일 04:00):  0 4 * * * /home/ubuntu/fitbet/deploy/backup.sh >> /home/ubuntu/fitbet/backups/backup.log 2>&1
#   Windows(Git Bash) 작업 스케줄러: "C:\Program Files\Git\bin\bash.exe" -lc "/c/fitbet-deploy/deploy/backup.sh"
set -eu
cd "$(dirname "$0")/.."
TS=$(date +%Y%m%d-%H%M)
mkdir -p backups

HOST_BACKUP_DIR="$PWD/backups"
if command -v cygpath >/dev/null 2>&1; then
  # Windows Git Bash: /c/... 경로를 Docker가 이해하는 C:\... 로 바꾸고, 자동 경로 변환(MSYS)을 끈다
  HOST_BACKUP_DIR=$(cygpath -w "$PWD/backups")
  export MSYS_NO_PATHCONV=1
fi

# 1) DB: 컨테이너 안의 mariadb-dump 실행 → 압축해서 호스트에 저장
docker compose exec -T db sh -c 'mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" --single-transaction fitbet' \
  | gzip > "backups/db-$TS.sql.gz"

# 2) 사진: uploads 볼륨을 tar로 묶기
docker compose run --rm --no-deps --user root -v "$HOST_BACKUP_DIR:/backup" --entrypoint tar app \
  czf "/backup/uploads-$TS.tgz" -C /data uploads

find backups -name '*.gz' -mtime +14 -delete
find backups -name '*.tgz' -mtime +14 -delete
echo "[$TS] backup done"
