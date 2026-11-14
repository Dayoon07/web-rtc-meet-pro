const APP = '/app'

export const ROUTES = {
  home: '/',
  pricing: '/pricing',
  terms: '/terms',
  privacy: '/privacy',
  licenses: '/licenses',

  login: '/login',
  signup: '/signup',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  verifyEmail: '/verify-email',
  verifyEmailConfirm: '/verify-email/confirm',
  oauthCallback: '/oauth/callback',
  onboarding: '/onboarding',

  meetPattern: '/meet/:meetingId',
  meet: (meetingId: number | string) => `/meet/${meetingId}`,

  joinPattern: '/join/:token',
  join: (token: string) => `/join/${token}`,

  app: APP,
  chat: (teamId?: number | string, roomId?: number | string) =>
    teamId == null ? `${APP}/chat` : roomId == null ? `${APP}/chat/${teamId}` : `${APP}/chat/${teamId}/${roomId}`,
  meetings: `${APP}/meetings`,
  meeting: (meetingId: number | string) => `${APP}/meetings/${meetingId}`,
  start: `${APP}/start`,
  teams: `${APP}/teams`,
  team: (teamId: number | string) => `${APP}/teams/${teamId}`,
  invitations: `${APP}/invitations`,
  billing: `${APP}/billing`,
  billingCard: `${APP}/billing/card`,
  settings: `${APP}/settings`,
} as const

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

export const DEFAULT_APP_ROUTE = ROUTES.chat()

export const safeRedirect = (value: string | null) => (value && value.startsWith('/') && !value.startsWith('//') ? value : DEFAULT_APP_ROUTE)
