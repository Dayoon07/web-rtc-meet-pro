/**
 * 화면이 부르는 서버 주소를 한곳에 모은다. 주소가 바뀌면 이 파일만 고친다.
 * 값은 /api 뒤의 경로 (앞의 /api 는 request() 가 붙인다). 예외: 웹소켓·구글 로그인처럼 /api 밖의 주소는 맨 아래 SERVER_PATHS
 */

/** 주소 한 칸에 넣을 값: '/' 같은 글자가 주소를 망가뜨리지 않게 */
const part = (value: string | number) => encodeURIComponent(String(value))

const AUTH = '/auth'
const USERS_ME = '/users/me'
const TEAMS = '/teams'
const INVITATIONS = '/invitations'
const CHAT = '/chat'
const MEETINGS = '/meetings'
const NOTIFICATIONS = '/notifications'
const SUBSCRIPTIONS = '/subscriptions'
const BILLING = '/billing'

export const API = {
  // ---- 로그인·가입 ----
  auth: {
    login: `${AUTH}/login`,
    signup: `${AUTH}/signup`,
    refresh: `${AUTH}/refresh`,
    logout: `${AUTH}/logout`,
    emailAvailability: (email: string) => `${AUTH}/email-availability?email=${part(email)}`,
    passwordResetRequest: `${AUTH}/password-reset/request`,
    passwordResetConfirm: `${AUTH}/password-reset/confirm`,
    emailVerificationConfirm: `${AUTH}/email-verification/confirm`,
    providers: `${AUTH}/providers`,
    oauthExchange: `${AUTH}/oauth/exchange`,
  },

  // ---- 내 정보 ----
  me: {
    root: USERS_ME,
    profile: `${USERS_ME}/profile`,
    profileImage: `${USERS_ME}/profile-image`,
    password: `${USERS_ME}/password`,
    emailVerification: `${USERS_ME}/email-verification`,
    withdraw: `${USERS_ME}/withdraw`,
  },

  // ---- 워크스페이스(팀) ----
  teams: {
    root: TEAMS,
    item: (teamId: number) => `${TEAMS}/${part(teamId)}`,
    invitations: (teamId: number) => `${TEAMS}/${part(teamId)}/invitations`,
    invitation: (teamId: number, invitationId: number) => `${TEAMS}/${part(teamId)}/invitations/${part(invitationId)}`,
    member: (teamId: number, userId: number) => `${TEAMS}/${part(teamId)}/members/${part(userId)}`,
    memberRole: (teamId: number, userId: number) => `${TEAMS}/${part(teamId)}/members/${part(userId)}/role`,
    leave: (teamId: number) => `${TEAMS}/${part(teamId)}/leave`,
    subscription: (teamId: number) => `${TEAMS}/${part(teamId)}/subscription`,
    purchase: (teamId: number) => `${TEAMS}/${part(teamId)}/subscription/purchase`,
    overage: (teamId: number) => `${TEAMS}/${part(teamId)}/overage`,
    meetings: (teamId: number) => `${TEAMS}/${part(teamId)}/meetings`,
    chatRooms: (teamId: number) => `${TEAMS}/${part(teamId)}/chat/rooms`,
    chatDirect: (teamId: number) => `${TEAMS}/${part(teamId)}/chat/rooms/direct`,
    chatGroup: (teamId: number) => `${TEAMS}/${part(teamId)}/chat/rooms/group`,
    chatCategories: (teamId: number) => `${TEAMS}/${part(teamId)}/chat/categories`,
  },

  // ---- 받은 초대 ----
  invitations: {
    mine: INVITATIONS,
    accept: (id: number) => `${INVITATIONS}/${part(id)}/accept`,
    decline: (id: number) => `${INVITATIONS}/${part(id)}/decline`,
  },

  // ---- 채팅 ----
  chat: {
    unreadCount: `${CHAT}/unread-count`,
    room: (roomId: number) => `${CHAT}/rooms/${part(roomId)}`,
    voice: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/voice`,
    members: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/members`,
    leave: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/leave`,
    notice: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/notice`,
    read: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/read`,
    files: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/files`,
    messages: (roomId: number, before?: number) => `${CHAT}/rooms/${part(roomId)}/messages${before ? `?before=${part(before)}` : ''}`,
    message: (messageId: number) => `${CHAT}/messages/${part(messageId)}`,
    category: (categoryId: number) => `${CHAT}/categories/${part(categoryId)}`,
  },

  // ---- 회의 ----
  meetings: {
    list: (scope: 'upcoming' | 'past') => `${MEETINGS}?scope=${part(scope)}`,
    item: (id: number) => `${MEETINGS}/${part(id)}`,
    cancel: (id: number) => `${MEETINGS}/${part(id)}/cancel`,
    join: (id: number) => `${MEETINGS}/${part(id)}/join`,
    recordings: (id: number) => `${MEETINGS}/${part(id)}/recordings`,
    recording: (id: number, recordingId: number) => `${MEETINGS}/${part(id)}/recordings/${part(recordingId)}`,
    recordingChunk: (id: number, recordingId: number, seq: number) =>
      `${MEETINGS}/${part(id)}/recordings/${part(recordingId)}/chunks?seq=${part(seq)}`,
    recordingFinish: (id: number, recordingId: number) => `${MEETINGS}/${part(id)}/recordings/${part(recordingId)}/finish`,
    notes: (id: number) => `${MEETINGS}/${part(id)}/notes`,
    notesState: (id: number) => `${MEETINGS}/${part(id)}/notes/state`,
    notesTranscript: (id: number) => `${MEETINGS}/${part(id)}/notes/transcript`,
    notesStart: (id: number) => `${MEETINGS}/${part(id)}/notes/start`,
    notesStop: (id: number) => `${MEETINGS}/${part(id)}/notes/stop`,
    notesLines: (id: number) => `${MEETINGS}/${part(id)}/notes/lines`,
    notesRegenerate: (id: number) => `${MEETINGS}/${part(id)}/notes/regenerate`,
    captionsState: (id: number) => `${MEETINGS}/${part(id)}/captions/state`,
    captionsStart: (id: number) => `${MEETINGS}/${part(id)}/captions/start`,
    captionsStop: (id: number) => `${MEETINGS}/${part(id)}/captions/stop`,
    captionsWatch: (id: number) => `${MEETINGS}/${part(id)}/captions/watch`,
    captionsLines: (id: number) => `${MEETINGS}/${part(id)}/captions/lines`,
  },

  // ---- 알림 ----
  notifications: {
    list: NOTIFICATIONS,
    unreadCount: `${NOTIFICATIONS}/unread-count`,
    read: (id: number) => `${NOTIFICATIONS}/${part(id)}/read`,
    readAll: `${NOTIFICATIONS}/read-all`,
  },

  // ---- 요금제·결제 ----
  plans: { list: '/plans' },
  subscriptions: {
    item: (subscriptionId: number) => `${SUBSCRIPTIONS}/${part(subscriptionId)}`,
    notices: `${SUBSCRIPTIONS}/notices`,
    acknowledgeExpiry: (subscriptionId: number) => `${SUBSCRIPTIONS}/${part(subscriptionId)}/acknowledge-expiry`,
  },
  billing: {
    cardPrepare: `${BILLING}/card/prepare`,
    card: `${BILLING}/card`,
    payments: `${BILLING}/payments`,
  },

  // ---- 화면 오류 신고 ----
  clientErrors: '/client-errors',
} as const

/** /api 밖의 서버 주소 (브라우저가 직접 여는 주소) */
export const SERVER_PATHS = {
  /** STOMP 웹소켓 (채팅·알림 실시간) */
  websocket: '/ws',
  /** mediasoup 사이드카 socket.io */
  mediaSocket: '/media/socket.io',
  /** 구글 로그인 시작 (Spring Security oauth2Login) */
  googleLogin: '/oauth2/authorization/google',
  /** 오픈소스 라이선스 목록 (scripts/oss-licenses.py 가 만든 정적 파일) */
  ossLicenses: '/oss-licenses.json',
} as const
