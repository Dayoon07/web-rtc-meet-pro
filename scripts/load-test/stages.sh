#!/usr/bin/env bash
# 단계별 실행: ./stages.sh <접두어> "<사용자 수들>" <유지 초> <들어오는 초> <메시지 간격 초>
# 단계마다 서버·DB 자원 기록(monitor.py)과 사용자 시나리오(run.mjs)를 함께 돌리고, 30초 쉰다
cd "$(dirname "$0")/../.."
prefix=$1; counts=$2; duration=$3; ramp=$4; every=$5
for n in $counts; do
  label="$prefix-$n"
  python scripts/load-test/monitor.py "$label" $((duration + ramp + 5)) 8082 > /dev/null &
  node scripts/load-test/run.mjs --users "$n" --duration "$duration" --ramp "$ramp" --msgEvery "$every" --label "$label" > "scripts/load-test/results/$label.log" 2>&1
  wait
  node -e "
    const r=require('./scripts/load-test/results/$label.json'), m=require('./scripts/load-test/results/$label-monitor.json').summary, L=r.latency;
    const errs=Object.values(r.errors).reduce((a,b)=>a+b,0), reqs=Object.entries(L).filter(([k])=>!['delivery','wsConnect'].includes(k)).reduce((a,[,v])=>a+v.n,0);
    console.log('$label', JSON.stringify({conn:r.maxConnected, reqPerSec:+(reqs/(r.durationSec+r.rampSec)).toFixed(0), errors:errs, errPct:+(100*errs/Math.max(1,reqs+errs)).toFixed(2),
      send:L.send&&[L.send.p50,L.send.p95,L.send.p99], rooms:L.rooms&&[L.rooms.p50,L.rooms.p95], delivery:L.delivery&&[L.delivery.p50,L.delivery.p95,L.delivery.p99], login:L.login&&[L.login.p50,L.login.p95],
      drops:r.counts.wsDropped||0, serverCpu:m.serverCpu, oracleCpu:m.oracleCpu, serverMem:m.serverMemMb.max}));"
  sleep 30
done
