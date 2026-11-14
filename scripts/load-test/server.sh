#!/usr/bin/env bash
# 부하 테스트용 서버를 (다시) 켠다: 포트 8082, CPU 2개 · 메모리 1.15GB, 메일 끔. 나머지 설정은 환경변수로 넘긴다
#   DB_POOL_SIZE=10 APP_SECURITY_MAX_CONCURRENT_PASSWORD_HASHES=1 ./scripts/load-test/server.sh
cd "$(dirname "$0")/../.."
LOG=${LOAD_SERVER_LOG:-scripts/load-test/results/server.log}
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue | % { Stop-Process -Id \$_.OwningProcess -Force }"
sleep 2
(cd apps/server && SERVER_PORT=8082 MAIL_HOST= STORAGE_TYPE=local ALLOWED_ORIGINS="http://localhost:5175" \
  nohup "C:/Program Files/Java/jdk-21/bin/java" -Xmx1152m -Xms512m -XX:ActiveProcessorCount=2 -jar build/libs/server-0.0.1-SNAPSHOT.jar > "../../$LOG" 2>&1 &)
until curl -s -o /dev/null http://localhost:8082/api/auth/providers; do sleep 2; done
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8082 -State Listen | % { (Get-Process -Id \$_.OwningProcess).ProcessorAffinity = [IntPtr]5 }"
echo "server up (pool=${DB_POOL_SIZE:-10}, hashes=${APP_SECURITY_MAX_CONCURRENT_PASSWORD_HASHES:-cpu*2})"
