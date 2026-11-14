export type PushConfig = { enabled: boolean; publicKey: string | null }

export type PushSubscribeRequest = { endpoint: string; p256dh: string; auth: string; lang?: string }
export type PushUnsubscribeRequest = { endpoint: string }
