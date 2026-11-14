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

  me: {
    root: USERS_ME,
    profile: `${USERS_ME}/profile`,
    profileImage: `${USERS_ME}/profile-image`,
    password: `${USERS_ME}/password`,
    emailVerification: `${USERS_ME}/email-verification`,
    withdraw: `${USERS_ME}/withdraw`,
    quietHours: `${USERS_ME}/quiet-hours`,
  },

  teams: {
    root: TEAMS,
    item: (teamId: number) => `${TEAMS}/${part(teamId)}`,
    invitations: (teamId: number) => `${TEAMS}/${part(teamId)}/invitations`,
    invitation: (teamId: number, invitationId: number) => `${TEAMS}/${part(teamId)}/invitations/${part(invitationId)}`,
    inviteLinks: (teamId: number) => `${TEAMS}/${part(teamId)}/invite-links`,
    inviteLink: (teamId: number, linkId: number) => `${TEAMS}/${part(teamId)}/invite-links/${part(linkId)}`,
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
    presence: (teamId: number) => `${TEAMS}/${part(teamId)}/presence`,
    chatSearch: (teamId: number, q: string) => `${TEAMS}/${part(teamId)}/chat/search?q=${part(q)}`,
  },

  inviteLinks: {
    item: (token: string) => `/invite-links/${part(token)}`,
    join: (token: string) => `/invite-links/${part(token)}/join`,
  },
  invitations: {
    mine: INVITATIONS,
    accept: (id: number) => `${INVITATIONS}/${part(id)}/accept`,
    decline: (id: number) => `${INVITATIONS}/${part(id)}/decline`,
  },

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
    thread: (messageId: number) => `${CHAT}/messages/${part(messageId)}/thread`,
    mute: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/mute`,
    typing: (roomId: number) => `${CHAT}/rooms/${part(roomId)}/typing`,
    reactions: (messageId: number) => `${CHAT}/messages/${part(messageId)}/reactions`,
    category: (categoryId: number) => `${CHAT}/categories/${part(categoryId)}`,
  },

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

  notifications: {
    list: NOTIFICATIONS,
    unreadCount: `${NOTIFICATIONS}/unread-count`,
    read: (id: number) => `${NOTIFICATIONS}/${part(id)}/read`,
    readAll: `${NOTIFICATIONS}/read-all`,
  },

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
    refund: (orderId: string) => `${BILLING}/payments/${part(orderId)}/refund`,
  },

  push: { config: '/push/config', subscriptions: '/push/subscriptions' },

  presence: '/presence',

  clientErrors: '/client-errors',
} as const

export const SERVER_PATHS = {
  websocket: '/ws',
  mediaSocket: '/media/socket.io',
  googleLogin: '/oauth2/authorization/google',
  ossLicenses: '/oss-licenses.json',
} as const
