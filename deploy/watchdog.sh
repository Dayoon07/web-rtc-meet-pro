#!/usr/bin/env bash
# 서버가 죽었을 때 바로 알림 (5분마다, cron). 12시간 상태 보고는 서버가 보내지만 서버가 죽으면 못 보내서 따로 둔다.
#   확인: 서버 · DB (/api/health 가 DB 까지 본다) · 미디어 사이드카 (/health) · 디스크 90% · 메모리 95%
#   문제가 새로 생기거나 다시 괜찮아질 때만 메일 (같은 문제로 5분마다 메일이 오지 않게)
#   설치 (서버에서 한 번):  crontab -e  →  */5 * * * * /opt/limeet/deploy/watchdog.sh >> /var/log/limeet-watchdog.log 2>&1
#   메일 설정은 deploy/.env 의 MAIL_HOST · MAIL_PORT · MAIL_USERNAME · MAIL_PASSWORD 를 쓴다 (curl 로 SMTP)
#   시험: WATCHDOG_DRY_RUN=1 이면 메일 대신 화면에 찍는다
set -u
cd "$(dirname "$0")"

# .env 는 셸 문법이 아니라서(값에 < > 공백) source 하지 않고 필요한 값만 읽는다
envval() { [ -f .env ] && grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'; }
MAIL_HOST=${MAIL_HOST:-$(envval MAIL_HOST)}
MAIL_PORT=${MAIL_PORT:-$(envval MAIL_PORT)}
MAIL_USERNAME=${MAIL_USERNAME:-$(envval MAIL_USERNAME)}
MAIL_PASSWORD=${MAIL_PASSWORD:-$(envval MAIL_PASSWORD)}
TO=${HEALTH_REPORT_TO:-$(envval HEALTH_REPORT_TO)}
TO=${TO:-gangd0642@gmail.com}
SERVER_HEALTH_URL=${SERVER_HEALTH_URL:-http://127.0.0.1:8080/api/health}
MEDIA_HEALTH_URL=${MEDIA_HEALTH_URL:-http://127.0.0.1:4000/health}
STATE=${WATCHDOG_STATE:-/var/lib/limeet-watchdog.state}
HOST=$(hostname)

problems=()
code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 "$SERVER_HEALTH_URL")
case "$code" in
  200) ;;
  503) problems+=("DB 응답 없음 (서버는 켜져 있음, $SERVER_HEALTH_URL → 503)") ;;
  000) problems+=("서버 응답 없음 (꺼졌거나 멈춤, $SERVER_HEALTH_URL)") ;;
  *) problems+=("서버 응답 이상 (HTTP $code, $SERVER_HEALTH_URL)") ;;
esac
curl -fs -o /dev/null -m 10 "$MEDIA_HEALTH_URL" || problems+=("미디어 사이드카 응답 없음 (화상회의·음성 안 됨, $MEDIA_HEALTH_URL)")
disk=$(df --output=pcent / 2>/dev/null | tail -1 | tr -dc '0-9')
[ "${disk:-0}" -ge 90 ] && problems+=("디스크 ${disk}% 사용 (df /)")
if [ -r /proc/meminfo ]; then
  # MemAvailable 이 없는 환경(오래된 커널 등)에서는 재지 않는다
  mem=$(awk '/^MemTotal:/ {t=$2} /^MemAvailable:/ {a=$2; ok=1} END { if (t > 0 && ok) printf "%d", (t-a)*100/t; else print 0 }' /proc/meminfo)
  [ "${mem:-0}" -ge 95 ] && problems+=("메모리 ${mem}% 사용")
fi

now=$(date '+%Y-%m-%d %H:%M')
current=$(printf '%s\n' "${problems[@]+"${problems[@]}"}")
previous=$(cat "$STATE" 2>/dev/null || true)

send_mail() {
  local subject="$1" body="$2"
  if [ -n "${WATCHDOG_DRY_RUN:-}" ] || [ -z "${MAIL_HOST:-}" ]; then
    printf '[메일 안 보냄%s] %s\n%s\n' "${MAIL_HOST:+: 시험}" "$subject" "$body"
    return
  fi
  local file
  file=$(mktemp)
  # 제목은 UTF-8 base64 (한글)
  printf 'From: MQ watchdog <%s>\r\nTo: %s\r\nSubject: =?UTF-8?B?%s?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n%s\r\n' \
    "$MAIL_USERNAME" "$TO" "$(printf '%s' "$subject" | base64 | tr -d '\n')" "$body" > "$file"
  curl -sS --ssl-reqd --url "smtp://${MAIL_HOST}:${MAIL_PORT:-587}" --user "${MAIL_USERNAME}:${MAIL_PASSWORD}" \
    --mail-from "$MAIL_USERNAME" --mail-rcpt "$TO" -T "$file" && echo "$now 메일 보냄: $subject"
  rm -f "$file"
}

if [ "$current" != "$previous" ]; then
  if [ -n "$current" ]; then
    send_mail "[MQ 경고] $HOST 문제 발생 ($now)" "$(printf '아래 문제가 생겼어요 (%s, %s):\n\n%s\n\n확인: docker compose ps / docker compose logs --tail 100 <서비스>\n다시 정상이 되면 회복 메일이 옵니다.' "$HOST" "$now" "$current")"
  else
    send_mail "[MQ 회복] $HOST 다시 정상 ($now)" "$(printf '이전 문제가 모두 해결됐어요 (%s, %s).\n\n이전 문제:\n%s' "$HOST" "$now" "$previous")"
  fi
fi
printf '%s' "$current" > "$STATE" 2>/dev/null || echo "상태 파일을 쓸 수 없음: $STATE (WATCHDOG_STATE 로 바꾸기)"
[ -n "$current" ] && echo "$now 문제: $current" || true
