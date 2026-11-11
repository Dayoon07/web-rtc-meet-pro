// 접속 상태. ONLINE = 접속 중, AWAY = 자리 비움, OFFLINE = 접속 안 함

export type PresenceStatus = 'ONLINE' | 'AWAY' | 'OFFLINE'

/** 서버가 돌려주는 값 · /user/queue/presence 실시간 이벤트 */
export type PresenceView = { userId: number; status: PresenceStatus }

/** 서버에 보내는 값: 자리 비움(true) / 돌아옴(false) */
export type PresenceRequest = { away: boolean }
