"""부하 테스트 중 서버(java)·DB(oracle)·미디어(node) CPU·메모리를 2초마다 기록한다.
   python scripts/load-test/monitor.py <label> <seconds> [server-port] [media-port]
   CPU 는 코어 하나 = 100% (서버를 코어 2개로 묶었으면 최대 200%). 결과: results/<label>-monitor.json
"""
import json
import sys
import time
from pathlib import Path

import psutil

label, seconds = sys.argv[1], int(sys.argv[2])
server_port = int(sys.argv[3]) if len(sys.argv) > 3 else 8082
media_port = int(sys.argv[4]) if len(sys.argv) > 4 else 0


def pid_on(port):
    for c in psutil.net_connections('tcp'):
        if c.laddr and c.laddr.port == port and c.status == psutil.CONN_LISTEN:
            return c.pid
    return None


def watch():
    procs = {}
    sp = pid_on(server_port)
    if sp:
        procs['server'] = [psutil.Process(sp)]
    procs['oracle'] = [p for p in psutil.process_iter(['name']) if (p.info['name'] or '').lower().startswith('oracle')]
    if media_port:
        mp = pid_on(media_port)
        if mp:
            root = psutil.Process(mp)
            procs['media'] = [root] + root.children(recursive=True)
    for group in procs.values():
        for p in group:
            p.cpu_percent(None)
    return procs


procs = watch()
rows = []
end = time.time() + seconds
while time.time() < end:
    time.sleep(2)
    row = {'t': round(time.time())}
    for name, group in procs.items():
        cpu = mem = 0.0
        for p in group:
            try:
                cpu += p.cpu_percent(None)
                mem += p.memory_info().rss / 1024 / 1024
            except psutil.Error:
                pass
        row[name + 'Cpu'] = round(cpu)
        row[name + 'MemMb'] = round(mem)
    row['systemCpu'] = psutil.cpu_percent(None)
    rows.append(row)

summary = {}
for key in rows[0] if rows else []:
    if key == 't':
        continue
    vals = [r[key] for r in rows]
    summary[key] = {'avg': round(sum(vals) / len(vals)), 'max': round(max(vals))}
out = Path(__file__).parent / 'results' / f'{label}-monitor.json'
out.write_text(json.dumps({'summary': summary, 'rows': rows}, indent=1))
print(json.dumps(summary))
