#!/usr/bin/env bash
# =====================================================================
# 매일 백업 (cron 으로 새벽에): DB 볼륨 + 채팅 첨부파일 + 프로필 사진 → BACKUP_DIR (+ 선택: S3)
#   sudo crontab -e
#   0 4 * * * /home/ubuntu/web-rtc-meet-pro/deploy/backup.sh >> /var/log/limeet-backup.log 2>&1
#
# - DB(oracle-data)는 쓰이는 중에 복사하면 깨질 수 있어서 server·oracle 을 잠깐(1~2분) 멈추고 압축한다
# - chat-files, uploads 는 켜 둔 채로 압축한다 (파일이 새로 생기기만 해서 괜찮다)
# - 환경변수: BACKUP_DIR(기본 /var/backups/limeet), KEEP_DAYS(기본 7)
# - deploy/.env 에 BACKUP_S3_URI=s3://버킷/limeet 가 있고 aws CLI 가 설정돼 있으면 S3 에도 올린다
# 복구 방법은 deploy/README.md 의 "5. 백업" 참고
# =====================================================================
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/limeet}"
KEEP_DAYS="${KEEP_DAYS:-7}"
STAMP="$(date +%Y%m%d-%H%M)"
COMPOSE=(docker compose -f "$DEPLOY_DIR/docker-compose.yml" --env-file "$DEPLOY_DIR/.env")
# 도커 볼륨 이름 = compose 프로젝트 이름(docker-compose.yml 의 name: limeet) + "_" + 볼륨 이름
PROJECT="limeet"
BACKUP_S3_URI="${BACKUP_S3_URI:-$(grep -E '^BACKUP_S3_URI=' "$DEPLOY_DIR/.env" 2>/dev/null | cut -d= -f2- || true)}"

log() { echo "[$(date '+%F %T')] $*"; }

# 볼륨 하나를 tar.gz 로 (파일 소유자 번호를 그대로 보존해야 복구한 DB 가 뜬다)
backup_volume() {
    local volume="$1" name="$2"
    docker run --rm -v "${PROJECT}_${volume}:/data:ro" -v "$BACKUP_DIR:/backup" debian:12-slim \
        tar --numeric-owner -czf "/backup/${name}-${STAMP}.tgz" -C /data .
}

# DB 를 켜고 healthy 가 된 뒤 서버를 켠다 (서버가 먼저 뜨면 DB 연결에 실패한다)
start_services() {
    "${COMPOSE[@]}" start oracle
    local id
    id="$("${COMPOSE[@]}" ps -q oracle)"
    for _ in $(seq 1 60); do
        [[ "$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null)" == "healthy" ]] && break
        sleep 5
    done
    "${COMPOSE[@]}" start server
}

mkdir -p "$BACKUP_DIR"
log "백업 시작 → $BACKUP_DIR"

# 1) DB: 서버와 DB 를 멈추고 볼륨 압축. 중간에 실패해도 반드시 다시 켠다
"${COMPOSE[@]}" stop server oracle
trap start_services EXIT
backup_volume oracle-data oracle
trap - EXIT
start_services
log "DB 백업 끝 (서버 다시 켬)"

# 2) 파일
backup_volume chat-files chat-files
backup_volume uploads uploads
log "파일 백업 끝"

# 3) 오래된 백업 정리
find "$BACKUP_DIR" -maxdepth 1 -name '*.tgz' -mtime +"$KEEP_DAYS" -delete

# 4) (선택) S3 에도 보관
if [[ -n "$BACKUP_S3_URI" ]]; then
    if command -v aws >/dev/null 2>&1; then
        aws s3 cp "$BACKUP_DIR" "$BACKUP_S3_URI/$STAMP/" --recursive --exclude '*' --include "*-${STAMP}.tgz"
        log "S3 업로드 끝 → $BACKUP_S3_URI/$STAMP/"
    else
        log "BACKUP_S3_URI 가 있지만 aws CLI 가 없어 S3 업로드는 건너뜀"
    fi
fi

log "백업 끝"
