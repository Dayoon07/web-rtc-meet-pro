// 화상회의 인원 테스트: 크롬(헤드리스, 가짜 카메라·마이크)을 여러 개 띄워 한 회의에 넣는다.
//   node scripts/load-test/meeting.mjs --people 6 --seconds 60 --web http://localhost:5175
// 팀(users.json 의 첫 팀) 팀장이 회의를 바로 시작하고, 팀원(필요하면 다른 팀 사람을 초대 링크로 더해서)이 들어간다.
// 각 크롬은 웹 클라이언트 그대로: 1회용 코드로 로그인 → /meet/{id} → 카메라·마이크를 보내고 다른 사람 것을 받는다
import { spawn } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { api, sleep } from './lib.mjs'

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc), []))
const PEOPLE = Number(args.people ?? 4)
const SECONDS = Number(args.seconds ?? 60)
const WEB = args.web ?? 'http://localhost:5175'
const CHROME = args.chrome ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe'

const all = JSON.parse(readFileSync(new URL('./results/users.json', import.meta.url), 'utf8'))
const team = all.filter((u) => u.teamId === all[0].teamId)
const owner = team[0]
const login = async (u) => (await api(null, 'login', 'POST', '/api/auth/login', { json: { email: u.email, password: u.password } }))?.accessToken

const ownerToken = await login(owner)
// 팀 인원이 모자라면 다른 팀 사람을 초대 링크로 이 팀에 더한다
const people = [...team]
if (PEOPLE > team.length) {
  const link = await api(null, 'link', 'POST', `/api/teams/${owner.teamId}/invite-links`, { token: ownerToken, json: {} })
  for (const u of all.filter((x) => x.teamId !== owner.teamId).slice(0, PEOPLE - team.length)) {
    const t = await login(u)
    const joined = await api(null, 'join', 'POST', `/api/invite-links/${link.token}/join`, { token: t, json: {} })
    if (joined) people.push(u)
  }
}
const meeting = await api(null, 'meeting', 'POST', `/api/teams/${owner.teamId}/meetings`, { token: ownerToken, json: { title: `부하 회의 ${PEOPLE}명`, durationMinutes: 60 } })
if (!meeting) throw new Error('회의를 만들지 못했어요')
console.log(`회의 ${meeting.id}: ${PEOPLE}명 들어가는 중`)

const browsers = []
for (const u of people.slice(0, PEOPLE)) {
  const token = u === owner ? ownerToken : await login(u)
  const { code } = await api(null, 'handoff', 'POST', '/api/sessions/web-handoff', { token })
  const dir = mkdtempSync(join(tmpdir(), 'mq-load-chrome-'))
  const url = `${WEB}/oauth/callback?code=${code}&next=${encodeURIComponent(`/meet/${meeting.id}`)}`
  const p = spawn(CHROME, [
    '--headless=new', `--user-data-dir=${dir}`, '--no-first-run', '--no-default-browser-check', '--mute-audio',
    '--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--autoplay-policy=no-user-gesture-required',
    '--window-size=1280,720', url,
  ], { stdio: 'ignore' })
  browsers.push({ p, dir })
  await sleep(1500)
}
console.log(`크롬 ${browsers.length}개 띄움, ${SECONDS}초 유지`)
await sleep(SECONDS * 1000)

// 회의 중인 사람 수 (서버 기록)
const detail = await api(null, 'meeting', 'GET', `/api/meetings/${meeting.id}`, { token: ownerToken })
console.log(JSON.stringify({ meetingId: meeting.id, people: PEOPLE, live: detail?.participantCount ?? detail?.participants?.length ?? null, status: detail?.status }))
for (const b of browsers) b.p.kill()
await sleep(3000)
// 크롬이 아직 잡고 있는 파일이 있으면 지우지 못해도 넘어간다 (임시 폴더라 나중에 지워진다)
for (const b of browsers) {
  try {
    rmSync(b.dir, { recursive: true, force: true, maxRetries: 3 })
  } catch {
    // 남은 파일은 무시
  }
}
await api(null, 'cancel', 'POST', `/api/meetings/${meeting.id}/cancel`, { token: ownerToken })
process.exit(0)
