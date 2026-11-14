export type PresenceStatus = 'ONLINE' | 'AWAY' | 'OFFLINE'

export type PresenceView = { userId: number; status: PresenceStatus }

export type PresenceRequest = { away: boolean }
