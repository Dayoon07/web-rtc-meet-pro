/**
 * 화면 경로를 한곳에 모은다. 라우터(app/router.tsx), Link to, a href, navigate 모두 여기 값을 쓴다.
 * 경로가 바뀌면 이 파일만 고친다.
 *   ROUTES.xxx          : 라우터에 등록하는 경로 (":teamId" 같은 자리 표시 포함)
 *   ROUTES.xxx(...)     : 값이 들어간 실제 주소를 만드는 함수 (Link, navigate 에서)
 */

const APP = '/app'

export const ROUTES = {
  // ---- 공개 화면 ----
  home: '/',
  pricing: '/pricing',
  terms: '/terms',
  privacy: '/privacy',
  licenses: '/licenses',

  // ---- 로그인·가입 ----
  login: '/login',
  signup: '/signup',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  verifyEmail: '/verify-email',
  verifyEmailConfirm: '/verify-email/confirm',
  oauthCallback: '/oauth/callback',
  onboarding: '/onboarding',

  // ---- 회의실 (앱 화면 밖, 전체 화면) ----
  meetPattern: '/meet/:meetingId',
  meet: (meetingId: number | string) => `/meet/${meetingId}`,

  // ---- 팀 초대 링크 (로그인 뒤, 앱 화면 밖) ----
  joinPattern: '/join/:token',
  join: (token: string) => `/join/${token}`,

  // ---- 앱 화면 ----
  app: APP,
  chat: (teamId?: number | string, roomId?: number | string) =>
    teamId == null ? `${APP}/chat` : roomId == null ? `${APP}/chat/${teamId}` : `${APP}/chat/${teamId}/${roomId}`,
  meetings: `${APP}/meetings`,
  meeting: (meetingId: number | string) => `${APP}/meetings/${meetingId}`,
  /** 팀이 없을 때: 팀 만들기 · 받은 초대 수락 */
  start: `${APP}/start`,
  teams: `${APP}/teams`,
  team: (teamId: number | string) => `${APP}/teams/${teamId}`,
  invitations: `${APP}/invitations`,
  billing: `${APP}/billing`,
  billingCard: `${APP}/billing/card`,
  settings: `${APP}/settings`,
} as const

/** /app 아래 자식 라우트 (router.tsx 의 children path: 앞의 /app/ 없이) */
export const APP_CHILD_ROUTES = {
  chat: 'chat/:teamId?/:roomId?',
  meetings: 'meetings',
  meeting: 'meetings/:meetingId',
  start: 'start',
  teams: 'teams',
  team: 'teams/:teamId',
  invitations: 'invitations',
  billing: 'billing',
  billingCard: 'billing/card',
  settings: 'settings',
} as const

/** 로그인하면 처음 가는 화면 (채팅 중심) */
export const DEFAULT_APP_ROUTE = ROUTES.chat()

/** 로그인 뒤 돌아갈 주소 (?redirect=). 오픈 리다이렉트 방지: 이 사이트 안 경로만, 아니면 처음 화면 */
export const safeRedirect = (value: string | null) => (value && value.startsWith('/') && !value.startsWith('//') ? value : DEFAULT_APP_ROUTE)
