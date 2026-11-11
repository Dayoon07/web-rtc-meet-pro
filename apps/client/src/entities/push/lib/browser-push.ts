import { currentLanguage } from '@/shared/i18n'
import { pushApi } from '../api/push-api'

const SW_URL = '/sw.js'

/** 이 브라우저가 웹 푸시를 지원하는지 (iOS 는 홈 화면에 추가한 앱에서만) */
export const pushSupported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

/** 서버 공개 키(base64url) → 브라우저 구독에 넣는 바이트 */
const keyBytes = (base64url: string) => {
  const b64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64url.length % 4)) % 4)
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
}

/** 지금 이 브라우저의 구독 (없으면 null) */
export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null
  const reg = await navigator.serviceWorker.getRegistration('/')
  return (await reg?.pushManager.getSubscription()) ?? null
}

/** 서버에 이 구독을 지금 로그인한 사람·언어로 등록 (같은 브라우저로 다른 사람이 로그인해도 맞게) */
async function register(sub: PushSubscription) {
  const json = sub.toJSON()
  await pushApi.subscribe({ endpoint: sub.endpoint, p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '', lang: currentLanguage() })
}

/** 알림 켜기: 권한 묻기 → 서비스 워커 등록 → 구독 → 서버에 등록. 권한을 거절하면 'denied' */
export async function enablePush(publicKey: string): Promise<'ok' | 'denied'> {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return 'denied'
  const reg = await navigator.serviceWorker.register(SW_URL)
  await navigator.serviceWorker.ready
  const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(publicKey) }))
  await register(sub)
  return 'ok'
}

/** 알림 끄기: 서버에서 지우고 브라우저 구독도 끊는다 */
export async function disablePush() {
  const sub = await currentSubscription()
  if (!sub) return
  await pushApi.unsubscribe(sub.endpoint).catch(() => undefined)
  await sub.unsubscribe()
}

/** 로그인한 동안: 이미 구독한 브라우저면 지금 사람·언어로 다시 등록 (앱에서 한 번) */
export async function syncPush() {
  const sub = await currentSubscription()
  if (sub && Notification.permission === 'granted') await register(sub).catch(() => undefined)
}

/** 로그아웃: 이 브라우저로 더 이상 그 사람 알림이 오지 않게 서버에서만 지운다 (구독은 남겨 다음 로그인 때 다시 등록) */
export async function forgetPushOnLogout() {
  const sub = await currentSubscription().catch(() => null)
  if (sub) await pushApi.unsubscribe(sub.endpoint).catch(() => undefined)
}

/** 서비스 워커가 "그 화면으로 가 줘" 하면 (알림을 눌렀을 때) */
export function onPushNavigate(go: (link: string) => void) {
  if (!pushSupported()) return () => undefined
  const handler = (e: MessageEvent) => {
    const data = e.data as { type?: string; link?: string } | null
    if (data?.type === 'mq:navigate' && data.link?.startsWith('/')) go(data.link)
  }
  navigator.serviceWorker.addEventListener('message', handler)
  return () => navigator.serviceWorker.removeEventListener('message', handler)
}
