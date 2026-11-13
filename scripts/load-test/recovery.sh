#!/usr/bin/env bash
# 회복 테스트: 사용자 300명이 쓰는 중에 서버를 강제로 끄고(장애) 20초 뒤 다시 켠다.
#   → 다시 연결될 때까지 걸린 시간, 그동안 실패한 요청, 서버가 "보냈다"고 답한 메시지가 DB 에 다 있는지
#   ./scripts/load-test/recovery.sh  (서버 jar 는 apps/server/build/libs, 포트 8082)
cd "$(dirname "$0")/../.."
JAVA="C:/Program Files/Java/jdk-21/bin/java"
LOG="${LOAD_SERVER_LOG:-scripts/load-test/results/recovery-server.log}"
label=recovery-300
start_ts=$(date +%s)

python scripts/load-test/monitor.py "$label" 330 8082 > /dev/null &
node scripts/load-test/run.mjs --users 300 --duration 270 --ramp 30 --msgEvery 20 --label "$label" > "scripts/load-test/results/$label.log" 2>&1 &
RUN=$!

sleep 120
echo "$(date +%T) 서버 강제 종료"
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8082 -State Listen | % { Stop-Process -Id \$_.OwningProcess -Force }"
down_ts=$(date +%s)
sleep 20
echo "$(date +%T) 서버 다시 시작"
(cd apps/server && SERVER_PORT=8082 MAIL_HOST= STORAGE_TYPE=local ALLOWED_ORIGINS="http://localhost:5175" "$JAVA" -Xmx1152m -Xms512m -XX:ActiveProcessorCount=2 -jar build/libs/server-0.0.1-SNAPSHOT.jar >> "../../$LOG" 2>&1 &)
until curl -s -o /dev/null http://localhost:8082/api/auth/providers; do sleep 1; done
up_ts=$(date +%s)
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8082 -State Listen | % { (Get-Process -Id \$_.OwningProcess).ProcessorAffinity = [IntPtr]5 }"
echo "서버가 꺼져 있던 시간: $((up_ts - down_ts))초 (강제 종료 → 다시 응답)"
wait $RUN
wait
echo "{\"startTs\": $start_ts, \"downTs\": $down_ts, \"upTs\": $up_ts}" > "scripts/load-test/results/$label-times.json"
