// 브라우저 푸시 (창을 닫아도 오는 알림)

/** enabled=false 면 서버에 VAPID 키가 없어 푸시를 못 보낸다 */
export type PushConfig = { enabled: boolean; publicKey: string | null }

/** 브라우저 PushSubscription.toJSON() 의 endpoint · keys + 알림 문장 언어 */
export type PushSubscribeRequest = { endpoint: string; p256dh: string; auth: string; lang?: string }
export type PushUnsubscribeRequest = { endpoint: string }
