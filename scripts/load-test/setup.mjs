// 부하 테스트 준비: load0001@mq-load.test ... 계정을 만들고 10명씩 팀 + 채널 하나로 묶는다.
//   node scripts/load-test/setup.mjs signup 2000      → 가입 (서버 메일은 꺼 두고 실행할 것: MAIL_HOST=)
//   (가입 뒤 DB 에서 이메일 인증 처리: scripts/load-test/verify.sql)
//   node scripts/load-test/setup.mjs teams            → 팀·초대 링크·채널
// 결과는 scripts/load-test/results/users.json (git 에 올리지 않음)
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { api, Stats } from './lib.mjs'

const OUT = new URL('./results/users.json', import.meta.url)
const PASSWORD = 'LoadTest!2026'
const TEAM_SIZE = 10
mkdirSync(new URL('./results/', import.meta.url), { recursive: true })

const userIdOf = (token) => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()).sub * 1

/** 동시에 limit 개씩 */
async function pool(items, limit, fn) {
  let i = 0
  await Promise.all(Array.from({ length: limit }, async () => {
    while (i < items.length) await fn(items[i++])
  }))
}

const [, , step, arg] = process.argv
const stats = new Stats()
const t0 = performance.now()

if (step === 'signup') {
  const n = Number(arg ?? 2000)
  const users = []
  const emails = Array.from({ length: n }, (_, i) => `load${String(i + 1).padStart(4, '0')}@mq-load.test`)
  await pool(emails, 8, async (email) => {
    const tokens = await api(stats, 'signup', 'POST', '/api/auth/signup', {
      json: {
        email, password: PASSWORD, nickname: email.slice(0, 8), realName: '부하테스트', phoneNumber: '+821000000000', countryCode: 'KR',
        birthYear: 1990, termsAgreed: true, privacyAgreed: true,
      },
    })
    if (tokens) users.push({ email, password: PASSWORD, userId: userIdOf(tokens.accessToken), token: tokens.accessToken })
    if (users.length % 200 === 0) console.log(`가입 ${users.length}/${n}`)
  })
  users.sort((a, b) => a.email.localeCompare(b.email))
  writeFileSync(OUT, JSON.stringify(users, null, 1))
  console.log(JSON.stringify(stats.summary((performance.now() - t0) / 1000), null, 1))
} else if (step === 'teams') {
  const users = JSON.parse(readFileSync(OUT, 'utf8'))
  // 토큰이 만료됐을 수 있어 팀장만 다시 로그인
  const teams = []
  for (let i = 0; i < users.length; i += TEAM_SIZE) teams.push(users.slice(i, i + TEAM_SIZE))
  await pool(teams, 4, async (members) => {
    const [owner, ...rest] = members
    for (const u of members) {
      const t = await api(stats, 'login', 'POST', '/api/auth/login', { json: { email: u.email, password: u.password } })
      u.token = t?.accessToken
    }
    const team = await api(stats, 'createTeam', 'POST', '/api/teams', { token: owner.token, json: { name: `부하 ${owner.email.slice(4, 8)}` } })
    if (!team) return
    const link = await api(stats, 'inviteLink', 'POST', `/api/teams/${team.id}/invite-links`, { token: owner.token, json: {} })
    for (const u of rest) await api(stats, 'join', 'POST', `/api/invite-links/${link.token}/join`, { token: u.token, json: {} })
    const room = await api(stats, 'createChannel', 'POST', `/api/teams/${team.id}/chat/rooms/group`, {
      token: owner.token,
      json: { name: '부하 채널', memberIds: rest.map((u) => u.userId), categoryId: null },
    })
    for (const u of members) Object.assign(u, { teamId: team.id, roomId: room?.id })
    if (team.id % 20 === 0) console.log(`팀 ${team.id}`)
  })
  writeFileSync(OUT, JSON.stringify(users, null, 1))
  console.log(`팀 ${new Set(users.map((u) => u.teamId)).size}개, 채널 있는 사람 ${users.filter((u) => u.roomId).length}명`)
  console.log(JSON.stringify(stats.summary((performance.now() - t0) / 1000), null, 1))
}
